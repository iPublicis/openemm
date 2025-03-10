/*

    Copyright (C) 2019 AGNITAS AG (https://www.agnitas.org)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

*/

package com.agnitas.service.impl;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import com.agnitas.beans.DynamicTag;
import com.agnitas.beans.factory.TagDetailsFactory;
import com.agnitas.service.AgnDynTagGroupResolver;
import com.agnitas.service.AgnTagResolver;
import com.agnitas.service.AgnTagResolverFactory;
import com.agnitas.service.AgnTagService;
import org.agnitas.beans.TagDetails;
import org.agnitas.beans.factory.DynamicTagFactory;
import org.agnitas.emm.core.velocity.VelocityCheck;
import org.agnitas.util.DynTagException;
import org.agnitas.util.MissingEndTagException;
import org.agnitas.util.MissingValueTagException;
import org.agnitas.util.UnclosedTagException;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Required;

public class AgnTagServiceImpl implements AgnTagService {
    private static final Logger logger = Logger.getLogger(AgnTagServiceImpl.class);

    private Pattern dynTagInQuotesPattern = Pattern.compile("\\[(?<type>agnDYN|agnDVALUE|gridPH)\\s+name=\'(?<name>.*)\'\\s+/\\]");
    private Pattern dynTagInDoubleQuotesPattern = Pattern.compile("\\[(?<type>agnDYN|agnDVALUE|gridPH)\\s+name=\"(?<name>.*?)\"\\s+/\\]");

    private DynamicTagFactory dynamicTagFactory;
    private TagDetailsFactory tagDetailsFactory;
    private AgnTagResolverFactory agnTagResolverFactory;

    @Override
    public List<DynamicTag> getDynTags(String content) throws DynTagException {
        return getDynTags(content, null);
    }

    @Override
    public List<DynamicTag> getDynTags(String content, AgnDynTagGroupResolver resolver) throws DynTagException {
        List<DynamicTag> tags = new ArrayList<>();
        if (StringUtils.isNotEmpty(content)) {
            DynamicTag tag;
            int position = 0;

            while ((tag = nextDynTag(content, position, resolver)) != null) {
                position = tag.getStartTagEnd();
                tags.add(tag);
            }
        }
        return tags;
    }

    @Override
    public List<TagDetails> collectTags(String content, Predicate<TagDetails> predicate) {
        if (StringUtils.isEmpty(content)) {
            return Collections.emptyList();
        }

        List<TagDetails> tags = new ArrayList<>();
        TagDetails tag;
        int position = 0;

        try {
            while ((tag = nextTag(content, position, "agn*")) != null) {
                int end = tag.getEndPos();

                if (!isDynamicTagName(tag.getTagName())) {
                    if (tag.getTagParameters() != null && predicate.test(tag)) {
                        tags.add(tag);
                    }
                }

                position = end + 1;
            }
        } catch (DynTagException e) {
            // Do nothing — there are no more tags.
        }

        return tags;
    }

    @Override
    public String resolveTags(String content, AgnTagResolver resolver) throws Exception {
        if (StringUtils.isEmpty(content)) {
            return content;
        }

        StringBuilder output = new StringBuilder(content);
        TagDetails tag;
        int position = 0;

        while ((tag = nextTag(output.toString(), position, "agn*")) != null) {
            String name = tag.getTagName();
            int begin = tag.getStartPos();
            int end = tag.getEndPos();

            if (isDynamicTagName(name)) {
                position = end + 1;
            } else {
                if (tag.getTagParameters() == null) {
                    throw new Exception("error.personalization_tag_parameter");
                }

                String value = resolver.resolve(tag);
                if (value == null) {
                    position = end + 1;
                } else {
                    output.replace(begin, end, value);
                    if (logger.isInfoEnabled()) {
                        logger.info("resolveTags: " + name + " value '" + value + "'");
                    }
                    position = begin;
                }
            }
        }

        return output.toString();
    }

    @Override
    public String resolveTags(String content, @VelocityCheck int companyId, int mailingId, int mailingListId, int customerId) throws Exception {
        return resolveTags(content, agnTagResolverFactory.create(companyId, mailingId, mailingListId, customerId));
    }

    @Override
    public String resolve(TagDetails tag, @VelocityCheck int companyId, int mailingId, int mailingListId, int customerId) {
        return agnTagResolverFactory.create(companyId, mailingId, mailingListId, customerId).resolve(tag);
    }

    @Override
    public boolean isContainsThirdPartyText(String text) {
        return StringUtils.isNotBlank(text.replaceAll(dynTagInQuotesPattern.toString(), StringUtils.EMPTY)
                        .replaceAll(dynTagInDoubleQuotesPattern.toString(), StringUtils.EMPTY));
    }

    private boolean isDynamicTagName(String name) {
        if (name == null) {
            return false;
        }

        switch (name) {
            case "agnDYN":
            case "agnDVALUE":
            case "gridPH":
                return true;
            default:
                return false;
        }
    }

    private DynamicTag nextDynTag(String content, int begin, AgnDynTagGroupResolver resolver) throws DynTagException {
        TagDetails openingTag;

        do {
            openingTag = nextTag(content, begin, "agnDYN", "gridPH");
            if (openingTag == null) {
                return null;
            }
            begin = openingTag.getEndPos();
        } while (openingTag.getTagParameters() == null);

        DynamicTag tag = dynamicTagFactory.newDynamicTag();

        tag.setDynName(openingTag.getName());
        tag.setStartTagStart(openingTag.getStartPos());
        tag.setStartTagEnd(openingTag.getEndPos());

        Map<String, String> parameters = openingTag.getTagParameters();
        if (parameters != null && resolver != null) {
            String name = parameters.get("group");
            if (StringUtils.isNotEmpty(name)) {
                tag.setGroup(resolver.resolve(name));
            }
        }

        // The grid placeholder tag is always standalone.
        if (openingTag.getTagName().equals("gridPH") || openingTag.getFullText().endsWith("/]")) {
            tag.setStandaloneTag(true);
        } else {
            tag.setStandaloneTag(false);

            TagDetails closingTag;
            int position = openingTag.getEndPos();
            do {
                closingTag = nextTag(content, position, "/agnDYN");
                if (closingTag == null) {
                    throw new MissingEndTagException(positionToLine(content, openingTag.getEndPos()), openingTag.getName());
                }
                position = closingTag.getEndPos();
            } while (!StringUtils.equals(openingTag.getName(), closingTag.getName()));

            TagDetails valueTag;
            position = openingTag.getEndPos();
            do {
                valueTag = nextTag(content, position, closingTag.getStartPos() - 1, "agnDVALUE");
                if (valueTag == null) {
                    throw new MissingValueTagException(positionToLine(content, openingTag.getEndPos()), openingTag.getName());
                }
                position = valueTag.getEndPos();
            } while (!StringUtils.equals(openingTag.getName(), valueTag.getName()));

            tag.setValueTagStart(valueTag.getStartPos());
            tag.setValueTagEnd(valueTag.getEndPos());
            tag.setEndTagStart(closingTag.getStartPos());
            tag.setEndTagEnd(closingTag.getEndPos());
        }

        return tag;
    }

    private TagDetails nextTag(String content, int begin, String... patterns) throws DynTagException {
        return nextTag(content, begin, 0, patterns);
    }

    private TagDetails nextTag(String content, int begin, int end, String... patterns) throws DynTagException {
        begin = index(content, '[', begin, end);
        while (begin >= 0) {
            for (String pattern : patterns) {
                boolean ambiguous = pattern.endsWith("*");

                // Check tag name prefix.
                if (content.regionMatches(begin + 1, pattern, 0, pattern.length() - (ambiguous ? 1 : 0))) {
                    int pos = index(content, ']', begin + 1, end);
                    if (pos < 0) {
                        // Opening tag is invalid (no right bracket found).
                        throw new UnclosedTagException(positionToLine(content, begin), pattern);
                    } else {
                        TagDetails tag = tagDetailsFactory.create();

                        tag.setStartPos(begin);
                        tag.setEndPos(pos + 1);
                        tag.setFullText(content.substring(begin, pos + 1));
                        tag.findTagParameters();

                        // Check entire tag name (not only prefix) unless pattern is ambiguous.
                        if (ambiguous || StringUtils.endsWith(pattern, tag.getTagName())) {
                            return tag;
                        }
                    }
                }
            }
            begin = index(content, '[', begin + 1, end);
        }
        return null;
    }

    private int index(String content, char c, int fromIndex, int toIndex) {
        int position = content.indexOf(c, fromIndex);
        if (position <= toIndex || toIndex <= fromIndex) {
            return position;
        }
        return -1;
    }

    private int positionToLine(String content, int position) {
        try (LineNumberReader reader = new LineNumberReader(new StringReader(content))) {
            // noinspection ResultOfMethodCallIgnored
            reader.skip(position);
            // Use 1-base numbering.
            return reader.getLineNumber() + 1;
        } catch (IOException e) {
            return -1;
        }
    }

    @Required
    public void setDynamicTagFactory(DynamicTagFactory dynamicTagFactory) {
        this.dynamicTagFactory = dynamicTagFactory;
    }

    @Required
    public void setTagDetailsFactory(TagDetailsFactory tagDetailsFactory) {
        this.tagDetailsFactory = tagDetailsFactory;
    }

    @Required
    public void setAgnTagResolverFactory(AgnTagResolverFactory agnTagResolverFactory) {
        this.agnTagResolverFactory = agnTagResolverFactory;
    }
}
