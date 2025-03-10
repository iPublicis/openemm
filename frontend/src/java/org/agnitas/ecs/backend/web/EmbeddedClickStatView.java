/*

    Copyright (C) 2019 AGNITAS AG (https://www.agnitas.org)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

*/

package org.agnitas.ecs.backend.web;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.agnitas.ecs.EcsGlobals;
import org.agnitas.ecs.EcsPreviewSize;
import org.agnitas.ecs.backend.service.EmbeddedClickStatService;
import org.agnitas.emm.core.commons.util.ConfigService;
import org.agnitas.emm.core.commons.util.ConfigValue;
import org.agnitas.util.AgnUtils;
import org.agnitas.util.HtmlUtils;
import org.apache.log4j.Logger;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.w3c.dom.Document;

import com.agnitas.messages.I18nString;

import cz.vutbr.web.domassign.DeclarationMap;

/**
 * Servlet for generating Embedded click statistics mailing HTML.
 * Gets mailing HTML-content for certain recipient and adds
 * hidden fields at the end of HTML that will be used by ECS-page
 * javascript to generate click-stat-labels and place them near links
 */
public class EmbeddedClickStatView extends HttpServlet {
	/** Serial version UID. */
	private static final long serialVersionUID = -8653541733669592674L;
	
	/** The logger. */
	private static final transient Logger logger = Logger.getLogger(EmbeddedClickStatView.class);

	private static final Charset ENCODING = StandardCharsets.UTF_8;

	public static final String PATH = "/ecs_view";
	
	protected ConfigService configService;

    private ConfigService getConfigService() {
		if (configService == null) {
			configService = (ConfigService) WebApplicationContextUtils.getWebApplicationContext(getServletContext()).getBean("ConfigService");
		}
		return configService;
	}

    /**
     * Method handles servlet work (see description of class). The input parameters are:
     * mailingId - id of mailing to get HTML content
     * recipientId - id of recipient to generate mailing content for
     * viewMode - view mode of Embedded click statistics
     * companyId - id of company
     *
     * @param req request
     * @param res response
     * @throws IOException
     * @throws ServletException
     */
	@Override
    public void service(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
		if (!AgnUtils.isUserLoggedIn(req)) {
			logger.error("User is not authorized");
			res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}

        try {
			EmbeddedClickStatService service = (EmbeddedClickStatService) WebApplicationContextUtils.getWebApplicationContext(
			        this.getServletContext()).getBean("EmbeddedClickStatService");

			String charsetPattern = "<meta http-equiv *= *\"content-type\".*charset *= *[A-Za-z0-9-.:_]*";

			res.setContentType("text/html");
			res.setCharacterEncoding(ENCODING.name());

			try (ServletOutputStream out = res.getOutputStream()) {

				final int companyId = AgnUtils.getCompanyID(req);
				final Locale locale = AgnUtils.getLocale(req);

				if (req.getParameter("mailingID") == null || req.getParameter("recipientId") == null || req.getParameter("viewMode") == null) {
				    logger.error("EmbeddedClickStatView: Parameters error (not enough parameters to show EmbeddedClickStat View)");
					String errorMsg = I18nString.getLocaleString("ecs.Error.NoParams", locale);
					out.write(errorMsg.getBytes(ENCODING));
				} else {
				    int mailingId = Integer.valueOf(req.getParameter("mailingID"));
				    int recipientId = Integer.valueOf(req.getParameter("recipientId"));
				    int viewMode = Integer.valueOf(req.getParameter("viewMode"));
				    if (recipientId == 0) {
						String errorMsg = I18nString.getLocaleString("ecs.Error.NoTestRecipients", locale);
				        out.write(errorMsg.getBytes(ENCODING));
				    } else {
				        String mailingContent = service.getMailingContent(mailingId, recipientId);
				        
				        Pattern pattern = Pattern.compile(charsetPattern, Pattern.CASE_INSENSITIVE);
				        Matcher matcher = pattern.matcher(mailingContent);
				        mailingContent = matcher.replaceAll("<meta http-equiv=\"content-type\" content=\"text/html; charset=utf-8");
	
				        if (viewMode != EcsGlobals.MODE_PURE_MAILING) {
				            mailingContent = service.addStatsInfo(mailingContent, viewMode, mailingId, companyId);
				        }

						EcsPreviewSize previewSize = null;
						if (AgnUtils.parameterNotBlank(req, "previewSize")) {
							previewSize = EcsPreviewSize.getForId(Integer.parseInt(req.getParameter("previewSize")));
						}

						String scriptsAndStyles = getScriptsAndStyles(previewSize);

				        if (mailingContent.toLowerCase().contains("</head>")) {
				            mailingContent = mailingContent.replaceAll("(?i)</head>", scriptsAndStyles + "\n</head>");
				        } else {
				            mailingContent = scriptsAndStyles + mailingContent;
				        }

						// The following block is a workaround for media queries processing bug of wkhtmltopdf tool (see GWUA-1086)
						if (previewSize != null) {
							// For PDF rendering
							final String media = "print";

							try {
								Document document = HtmlUtils.parseDocument(mailingContent, ENCODING.name());

								URL base = null;
								try {
									base = new URL(getConfigService().getValue(ConfigValue.SystemUrl));
								} catch (MalformedURLException e) {
									logger.error("Error occurred: " + e.getMessage(), e);
								}

								DeclarationMap declarationMap = HtmlUtils.getDeclarationMap(document, ENCODING.name(), base);
								HtmlUtils.StylesEmbeddingOptions options = HtmlUtils.stylesEmbeddingOptionsBuilder()
										.setEncoding(ENCODING)
										.setBaseUrl(base)
										.setMediaType(media)
										.setEscapeAgnTags(true)
										.setPrettyPrint(false)
										.setUseNewLib(configService.getBooleanValue(ConfigValue.UseNewCssLibForStylesEmbedding, companyId))
										.build();

								mailingContent = HtmlUtils.embedStyles(document, declarationMap, options);
							} catch (Exception e) {
								logger.error("Error occurred: " + e.getMessage(), e);
							}
						}

						out.write(mailingContent.getBytes(ENCODING));
				    }
				}
			}
		} catch (Exception e) {
			logger.error("Error occurred: " + e.getMessage(), e);
		}
    }

	private String getScriptsAndStyles(EcsPreviewSize previewSize) {
		final String contextPath = getConfigService().getValue(ConfigValue.SystemUrl);

		return  "<script type=\"text/javascript\" src=\"" + contextPath + "/js/lib/jquery-1.6.2.min.js\"></script>\n" +
				"<script type=\"text/javascript\" src=\"" + contextPath + "/js/lib/ecs/statLabelAdjuster.js\"></script>\n" +
				"<style>\n" +
				getCssStyles(previewSize) +
				"</style>\n";
	}

	private String getCssStyles(EcsPreviewSize previewSize) {
		if (previewSize == null) {
			return "";
		}

		return  "html {\n" +
				"    width: " + previewSize.getWidth() + "px;\n" +
				"}\n" +
				"body {\n" +
				"    margin: 0;\n" +
				"}\n";
	}
}
