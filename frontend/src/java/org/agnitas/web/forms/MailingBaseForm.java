/*

    Copyright (C) 2019 AGNITAS AG (https://www.agnitas.org)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

*/

package org.agnitas.web.forms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.mail.internet.InternetAddress;
import javax.servlet.http.HttpServletRequest;

import org.agnitas.beans.Campaign;
import org.agnitas.beans.Mailing;
import org.agnitas.beans.MailingBase;
import org.agnitas.beans.Mailinglist;
import org.agnitas.beans.Mediatype;
import org.agnitas.util.AgnUtils;
import org.agnitas.web.MailingBaseAction;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.log4j.Logger;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;

import com.agnitas.beans.MediatypeEmail;
import com.agnitas.beans.TargetLight;
import com.agnitas.emm.core.mediatypes.common.MediaTypes;
import com.agnitas.service.AgnTagService;

public class MailingBaseForm extends StrutsFormBase {
	
	/** The logger. */
	private static final transient Logger logger = Logger.getLogger(MailingBaseForm.class);
    
	/** Serial version UID. */
    private static final long serialVersionUID = 8995916091799817822L;
    
    public static final int TEXTAREA_WIDTH = 75;

	/** 
     * Holds value of property mailinglistID. 
     */
    private int mailingID;
    
    /**
     * Holds value of property campaignID. 
     */
    private int campaignID;
    
    /**
     * Holds value of property shortname. 
     */
    protected String shortname = "";
    
    /**
     * Holds value of property description. 
     */
    private String description;
    
    /**
     * Holds value of property emailCharset. 
     */
    private String emailCharset;
    
    /**
     * Holds value of property action. 
     */
    protected int action;
    
    /**
     * Holds value of property emailLinefeed.
     */
    private int emailLinefeed;
    
    /**
     * Holds value of property mailingType. 
     */
    protected int mailingType;
    
    /**
     * Holds value of property targetID. 
     */
    protected int targetID;
    
    /**
     * Holds value of property mailinglistID. 
     */
    protected int mailinglistID;
    
    /**
     * Holds value of property templateID. 
     */
    private int templateID;
    
    /**
     * Holds value of property worldMailingSend.
     */
    protected boolean worldMailingSend;
     
    /**
     * Holds value of property showTemplate.
     */
    private boolean showTemplate;
  
    /**
     * Holds value of property targetGroups.
     */
    protected Collection<Integer> targetGroups;
    
    /**
     * Holds value of property isTemplate.
     */
    private boolean isTemplate=false;
    
    private boolean isGrid=false;
    
    /**
     * Holds value of property oldMailingID.
     */
    private int oldMailingID;

	/**
     * Holds value of property oldMailFormat.
     */
	private int oldMailFormat;

    /**
     * Is current mailing created with "Create followup mailing" button
     */
    private boolean isCreatedAsFollowUp;

    /**
     * Is current mailing created with "Copy" button
     */
    private boolean copiedMailing;
    
    /**
     * Holds value of property needsTarget.
     */
    private boolean needsTarget;
    
    /**
     * Holds value of property targetMode.
     */
    private int targetMode = Mailing.TARGET_MODE_OR;

    private boolean searchEnabled;

    private boolean contentSearchEnabled;

    /**
     * Holds value of property replyEmail.
     */
    protected String emailReplytoEmail;
    
    /**
     * Holds value of property replyFullname.
     */
    protected String emailReplytoFullname;

    protected List<Map<String, String>> actions;

    protected ActionMessages messages;

    protected ActionMessages errors;

    /**
     * Code for previous action (used in "confirm delete" dialogs to jump back to last view on cancel)
     */
    protected int previousAction;

    /**
     * If this is new mailing or a template or user has no mailing.settings.hide permission
     */
    private boolean canChangeEmailSettings;
    
    
    protected boolean templateContainerVisible;
    protected boolean otherMediaContainerVisible;
    protected boolean generalContainerVisible;
    protected boolean targetgroupsContainerVisible;
    protected boolean mailingIntervalContainerVisible;
	protected boolean parameterContainerVisible;

    /**
     * Does user have permission to see/edit mailinglist of current mailing
     */
    private boolean canChangeMailinglist = false;

    /**
     * Is mailing not active {@link com.agnitas.emm.core.maildrop.service.MaildropService#isActiveMailing(int, int)}
     * or user has permission {@link com.agnitas.emm.core.Permission#MAILING_CONTENT_CHANGE_ALWAYS}
     */
    private boolean mailingEditable = false;

    /**
	 * @return the templateContainerVisible
	 */
	public boolean isTemplateContainerVisible() {
		return templateContainerVisible;
	}

	/**
	 * @param templateContainerVisible the templateContainerVisible to set
	 */
	public void setTemplateContainerVisible(boolean templateContainerVisible) {
		this.templateContainerVisible = templateContainerVisible;
	}

	/**
	 * @return the otherMediaContainerVisible
	 */
	public boolean isOtherMediaContainerVisible() {
		return otherMediaContainerVisible;
	}

	/**
	 * @param otherMediaContainerVisible the otherMediaContainerVisible to set
	 */
	public void setOtherMediaContainerVisible(boolean otherMediaContainerVisible) {
		this.otherMediaContainerVisible = otherMediaContainerVisible;
	}

	/**
	 * @return the generalContainerVisible
	 */
	public boolean isGeneralContainerVisible() {
		return generalContainerVisible;
	}

	/**
	 * @param generalContainerVisible the generalContainerVisible to set
	 */
	public void setGeneralContainerVisible(boolean generalContainerVisible) {
		this.generalContainerVisible = generalContainerVisible;
	}

	public boolean isParameterContainerVisible() {
		return parameterContainerVisible;
	}

	public void setParameterContainerVisible(boolean parameterContainerVisible) {
		this.parameterContainerVisible = parameterContainerVisible;
	}

	/**
	 * @return the targetgroupsContainerVisible
	 */
	public boolean isTargetgroupsContainerVisible() {
		return targetgroupsContainerVisible;
	}

	/**
	 * @param targetgroupsContainerVisible the targetgroupsContainerVisible to set
	 */
	public void setTargetgroupsContainerVisible(boolean targetgroupsContainerVisible) {
		this.targetgroupsContainerVisible = targetgroupsContainerVisible;
	}

    /**
     * @return the mailingIntervalContainerVisible
     */
    public boolean isMailingIntervalContainerVisible() {
        return mailingIntervalContainerVisible;
    }

    /**
     * @param mailingIntervalContainerVisible the mailingIntervalContainerVisible to set
     */
    public void setMailingIntervalContainerVisible(boolean mailingIntervalContainerVisible) {
        this.mailingIntervalContainerVisible = mailingIntervalContainerVisible;
    }

    /**
     * Holds list of MailingBase.
     */
    protected List<MailingBase> templateMailingBases;

    /**
     * Holds template shortname selected by templateID.
     */
    protected String templateShortname;

    /**
     * Holds list of mailing lists.
     */
    protected List<Mailinglist> mailingLists;

    /**
     * Holds list of campaigns.
     */
    protected List<Campaign> campaigns;

    /**
     * Holds list of targets.
     */
    protected List<TargetLight> targets;

    /**
     * Holds list of targets selected by ids from targetGroups.
     */
    protected List<TargetLight> targetGroupsList;

    
	/** 
     * Creates a new instance of TemplateForm 
     */
    public MailingBaseForm() {
    }

    /**
     * Initialization.
     */
    public void clearData() throws Exception {
        clearData(false);
    }

    /**
     * Initialization
     * @param keepContainerVisibilityState - keep UI container folding state
     * @throws Exception
     */
    public void clearData(boolean keepContainerVisibilityState) throws Exception {
        this.targetID = 0;
        this.mailinglistID = 0;
        this.templateID = 0;
        this.campaignID = 0;
        this.mailingType = Mailing.TYPE_NORMAL;
        
        this.shortname = "";
        this.description = "";
       
        mediatypes = new HashMap<>();
        Mediatype mt = (Mediatype) getWebApplicationContext().getBean("MediatypeEmail");
        mt.setStatus(Mediatype.STATUS_ACTIVE);
        mediatypes.put(MediaTypes.EMAIL.getMediaCode(), mt);
        this.emailReplytoEmail = "";
        this.emailReplytoFullname = "";
        this.emailCharset = "UTF-8";
        this.emailLinefeed = 72;
        this.emailOnepixel = MediatypeEmail.ONEPIXEL_TOP;
        
        this.worldMailingSend = false;
        this.targetGroups = null;
        this.showTemplate = false;
        this.copiedMailing = false;
        this.isCreatedAsFollowUp = false;
        this.archived = false;
        this.needsTarget = false;
        this.targetMode = Mailing.TARGET_MODE_AND;

        if (!keepContainerVisibilityState) {
            this.templateContainerVisible = false;
            this.otherMediaContainerVisible = false;
            this.generalContainerVisible = false;
            this.targetgroupsContainerVisible = false;
        }
    }
    
    @Override
    public void reset(ActionMapping map, HttpServletRequest request) {
    	this.archived = false;
        this.templateContainerVisible = false;
        this.otherMediaContainerVisible = false;
        this.generalContainerVisible = false;
        this.targetgroupsContainerVisible = false;
		this.parameterContainerVisible = false;
        this.dynamicTemplate = false;

    	super.reset(map, request);
    }
    
    /**
     * Validate the properties that have been set from this HTTP request,
     * and return an <code>ActionErrors</code> object that encapsulates any
     * validation errors that have been found.  If no errors are found, return
     * <code>null</code> or an <code>ActionErrors</code> object with no
     * recorded error messages.
     * 
     * @param mapping The mapping used to select this instance
     * @param request The servlet request we are processing
     * @return errors
     */
    @Override
    public ActionErrors formSpecificValidate(ActionMapping mapping, HttpServletRequest request) {
        ActionErrors errors = new ActionErrors();

        if (action == MailingBaseAction.ACTION_SAVE) {
            if (this.shortname.length() < 3) {
                errors.add("shortname", new ActionMessage("error.name.too.short"));
            }

        	if (this.shortname.length() > 99) {
                errors.add("shortname", new ActionMessage("error.shortname_too_long"));
            }

        	if (this.mailinglistID == 0) {
        		errors.add("global", new ActionMessage("error.mailing.noMailinglist"));
        	}

            if (!this.isIsTemplate() && this.isNeedsTarget() && this.targetGroups == null) {
                errors.add("global", new ActionMessage("error.mailing.rulebased_without_target"));
            }

            if (this.emailReplytoFullname != null && this.emailReplytoFullname.length() > 255) {
                errors.add("replyFullname", new ActionMessage("error.reply_fullname_too_long"));
            }
            if (getSenderFullname() != null && getSenderFullname().length() > 255) {
                errors.add("senderFullname", new ActionMessage("error.sender_fullname_too_long"));
            }
            if (this.emailReplytoFullname != null && this.emailReplytoFullname.trim().length() == 0) {
                this.emailReplytoFullname = getSenderFullname();
            }
            
            if (this.targetGroups == null && this.mailingType == Mailing.TYPE_DATEBASED) {
                errors.add("global", new ActionMessage("error.mailing.rulebased_without_target"));
            }
            
            if (this.targetGroups == null && this.mailingType == Mailing.TYPE_INTERVAL) {
                errors.add("global", new ActionMessage("error.mailing.rulebased_without_target"));
            }

            if (getMediaEmail().getFromEmail().length() < 3) {
                errors.add("email", new ActionMessage("error.invalid.email"));
            }
            
            if (getEmailSubject().length() < 2) {
                errors.add("subject", new ActionMessage("error.mailing.subject.too_short"));
            }

            try {
                InternetAddress adr = new InternetAddress(getMediaEmail().getFromEmail());
                String email = adr.getAddress();
                if (!AgnUtils.isEmailValid(email)) {
                    errors.add("sender", new ActionMessage("error.mailing.sender_adress"));
                }              
            } catch (Exception e) {
                if(!getMediaEmail().getFromEmail().contains("[agn")) {
                    errors.add("sender", new ActionMessage("error.mailing.sender_adress"));
                }
            }

            AgnTagService agnTagService = getWebApplicationContext().getBean("AgnTagService", AgnTagService.class);
            try {
                agnTagService.getDynTags(getEmailSubject());
                agnTagService.getDynTags(getSenderFullname());
            } catch (Exception e) {
                logger.error("validate: " + e);
                errors.add("subject", new ActionMessage("error.template.dyntags"));
            }

            try {
                int companyId = AgnUtils.getCompanyID(request);
                agnTagService.resolveTags(getEmailSubject(), companyId, 0, 0, 0);
                agnTagService.resolveTags(getSenderFullname(), companyId, 0, 0, 0);
            } catch (Exception e) {
                errors.add("subject", new ActionMessage("error.personalization_tag"));
            }

//            if(getTextTemplate().length() != 0) {
//                // Just a syntax-check, no MailingID required
//                aMailing = (Mailing) getWebApplicationContext().getBean("Mailing");
//                aMailing.setCompanyID(this.getCompanyID(request));
//                
//                try {
//                    aMailing.personalizeText(this.getTextTemplate(), 0, this.getWebApplicationContext());
//                } catch (Exception e) {
//                    errors.add("texttemplate", new ActionMessage("error.personalization_tag"));
//                }
//                
//                try {
//                    aMailing.findDynTagsInTemplates(getTextTemplate(), this.getWebApplicationContext());
//                } catch (Exception e) {
//                    errors.add("texttemplate", new ActionMessage("error.template.dyntags"));
//                }
//                
//            }
//            
//            if(getHtmlTemplate().length() != 0) {
//                // Just a syntax-check, no MailingID required
//                aMailing = (Mailing) getWebApplicationContext().getBean("Mailing");
//                aMailing.setCompanyID(this.getCompanyID(request));
//                
//                try {
//                    aMailing.personalizeText(this.getHtmlTemplate(), 0, this.getWebApplicationContext());
//                } catch (Exception e) {
//                    errors.add("texttemplate", new ActionMessage("error.personalization_tag"));
//                }
//                
//                try {
//                    aMailing.findDynTagsInTemplates(getHtmlTemplate(), this.getWebApplicationContext());
//                } catch (Exception e) {
//                    logger.error("validate: find "+e);
//                    errors.add("texttemplate", new ActionMessage("error.template.dyntags"));
//                }
//            }
        }
        return errors;
    }

    @Override
    protected ActionMessages checkForHtmlTags(HttpServletRequest request) {
        if(action != MailingBaseAction.ACTION_VIEW_WITHOUT_LOAD){
            return super.checkForHtmlTags(request);
        }
        return new ActionErrors();
    }
    
    /**
     * Getter for property templateID.
     *
     * @return Value of property templateID.
     */
    public int getMailingID() {
        return this.mailingID;
    }
    
    /**
     * Setter for property templateID.
     * 
     * @param mailingID New value of property mailingID.
     */
    public void setMailingID(int mailingID) {
        this.mailingID = mailingID;
    }
    
    /**
     * Getter for property campaignID.
     *
     * @return Value of property campaignID.
     */
    public int getCampaignID() {
        return this.campaignID;
    }
    
    /**
     * Setter for property campaignID.
     * 
     * @param campaignID New value of property campaignID.
     */
    public void setCampaignID(int campaignID) {
        this.campaignID = campaignID;
    }
    
    /**
     * Getter for property shortname.
     *
     * @return Value of property shortname.
     */
    public String getShortname() {
        return shortname;
    }
    
    /** 
     * Setter for property shortname.
     *
     * @param shortname New value of property shortname.
     */
    public void setShortname(String shortname) {
        this.shortname = shortname;
    }
    
    /**
     * Getter for property description.
     *
     * @return Value of property description.
     */
    public String getDescription() {
        return this.description;
    }
    
    /** 
     * Setter for property description.
     *
     * @param description New value of property description.
     */
    public void setDescription(String description) {
        this.description = description;
    }
    
    /**
     * Getter for property charset.
     *
     * @return Value of property charset.
     */
    public String getEmailCharset() {
        return this.emailCharset;
    }
    
    /**
     * Setter for property charset.
     * 
     * @param emailCharset New value of property emailCharset.
     */
    public void setEmailCharset(String emailCharset) {
        this.emailCharset = emailCharset;
    }
    
    /**
     * Getter for property action.
     *
     * @return Value of property action.
     */
    public int getAction() {
        return this.action;
    }
    
    /** 
     * Setter for property action.
     *
     * @param action New value of property action.
     */
    public void setAction(int action) {
        this.action = action;
    }
    
    /** 
     * Getter for property subject.
     *
     * @return Value of property subject.
     */
    public String getEmailSubject() {
        return getMediaEmail().getSubject();
    }
    
    /** 
     * Setter for property subject.
     *
     * @param subject New value of property subject.
     */
    public void setEmailSubject(String subject) {
        getMediaEmail().setSubject(subject);
    }
    
    /** 
     * Getter for property emailLinefeed.
     *
     * @return Value of property emailLinefeed.
     */
    public int getEmailLinefeed() {
        return this.emailLinefeed;
    }
    
    /**
     * Setter for property emailLinefeed.
     *
     * @param emailLinefeed New value of property emailLinefeed.
     */
    public void setEmailLinefeed(int emailLinefeed) {
        this.emailLinefeed = emailLinefeed;
    }
    
    /**
     * Getter for property mailingType.
     *
     * @return Value of property mailingType.
     */
    public int getMailingType() {
        return this.mailingType;
    }
    
    /**
     * Setter for property mailingType.
     *
     * @param mailingType New value of property mailingType.
     */
    public void setMailingType(int mailingType) {
        this.mailingType = mailingType;
    }
    
    /** 
     * Getter for property targetID.
     *
     * @return Value of property targetID.
     */
    public int getTargetID() {
        return this.targetID;
    }
    
    /**
     * Setter for property targetID.
     *
     * @param targetID New value of property targetID.
     */
    public void setTargetID(int targetID) {
        this.targetID = targetID;
    }
    
    /** 
     * Getter for property mailinglistID.
     *
     * @return Value of property mailinglistID.
     */
    public int getMailinglistID() {
        return this.mailinglistID;
    }
    
    /**
     * Setter for property mailinglistID.
     *
     * @param mailinglistID New value of property mailinglistID.
     */
    public void setMailinglistID(int mailinglistID) {
        this.mailinglistID = mailinglistID;
    }
    
    /**
     * Getter for property templateID.
     *
     * @return Value of property templateID.
     */
    public int getTemplateID() {
        return this.templateID;
    }
    
    /**
     * Setter for property templateID.
     *
     * @param templateID New value of property templateID.
     */
    public void setTemplateID(int templateID) {
        this.templateID = templateID;
    }
    
    /**
     * Getter for property worldMailingSend.
     *
     * @return Value of property worldMailingSend.
     */
    public boolean isWorldMailingSend() {
        return this.worldMailingSend;
    }
    
    /**
     * Setter for property worldMailingSend.
     *
     * @param worldMailingSend New value of property worldMailingSend.
     */
    public void setWorldMailingSend(boolean worldMailingSend) {
        this.worldMailingSend = worldMailingSend;
    }
    
    
    /**
     * Getter for property htmlTemplate.
     * 
     * @return Value of property htmlTemplate.
     */
    public final String getHtmlTemplate() {
        final String template = getMediaEmail().getHtmlTemplate();
    	
    	return template;
    }
    
    /**
     * Setter for property htmlTemplate.
     *
     * @param htmlTemplate New value of property htmlTemplate.
     */
    public final void setHtmlTemplate(String htmlTemplate) {
        getMediaEmail().setHtmlTemplate(htmlTemplate);
    }
    
    /**
     * Getter for property textTemplate.
     *
     * @return Value of property textTemplate.
     */
    public final String getTextTemplate() {
        final String template = getMediaEmail().getTemplate();
        
        return template;
    }
    
    /**
     * Setter for property textTemplate.
     *
     * @param textTemplate New value of property textTemplate.
     */
    public final void setTextTemplate(String textTemplate) {
        getMediaEmail().setTemplate(textTemplate);
    }
    
    /**
     * Getter for property showTemplate.
     *
     * @return Value of property showTemplate.
     */
    public boolean isShowTemplate() {
        return this.showTemplate;
    }
    
    /**
     * Setter for property showTemplate.
     *
     * @param showTemplate New value of property showTemplate.
     */
    public void setShowTemplate(boolean showTemplate) {
        this.showTemplate = showTemplate;
    }
    
    /**
     * Getter for property targetGroups.
     *
     * @return Value of property targetGroups.
     */
    public Collection<Integer> getTargetGroups() {
        return this.targetGroups;
    }
    
    /**
     * Setter for property targetGroups.
     *
     * @param targetGroups New value of property targetGroups.
     */
    public void setTargetGroups(Collection<Integer> targetGroups) {
        this.targetGroups = targetGroups;
    }

	public Integer[] getTargetGroupIds() {
		if (CollectionUtils.isNotEmpty(targetGroups)) {
			return targetGroups.toArray(new Integer[targetGroups.size()]);
		} else {
			return ArrayUtils.EMPTY_INTEGER_OBJECT_ARRAY;
		}
	}

	public void setTargetGroupIds(Integer[] targetGroupIds) {
		targetGroups = new ArrayList<>(Arrays.asList(targetGroupIds));
	}

    /**
     * Getter for property isTemplate.
     *
     * @return Value of property isTemplate.
     */
    public boolean isIsTemplate() {
        return this.isTemplate;
    }
    
    /**
     * Setter for property isTemplate.
     *
     * @param isTemplate New value of property isTemplate.
     */
    public void setIsTemplate(boolean isTemplate) {
        this.isTemplate = isTemplate;
    }
    
    public boolean isIsGrid() {
		return isGrid;
	}

	public void setGrid(boolean isGrid) {
		this.isGrid = isGrid;
	}

	/**
     * Getter for property oldMailingID.
     *
     * @return Value of property oldMailingID.
     */
    public int getOldMailingID() {
        return this.oldMailingID;
    }
    
    /**
     * Setter for property oldMailingID.
     *
     * @param oldMailingID New value of property oldMailingID.
     */
    public void setOldMailingID(int oldMailingID) {
        this.oldMailingID = oldMailingID;
    }
    
    /**
     * @return isCreatedAsFollowUp || copiedMailing
     */
    public boolean isCopyFlag() {
        return isCreatedAsFollowUp || copiedMailing;
    }

    public boolean isCreatedAsFollowUp() {
        return isCreatedAsFollowUp;
    }

    public void setCreatedAsFollowUp(boolean createdAsFollowUp) {
        this.isCreatedAsFollowUp = createdAsFollowUp;
    }

    public boolean isCopiedMailing() {
        return copiedMailing;
    }

    public void setCopiedMailing(boolean copiedMailing) {
        this.copiedMailing = copiedMailing;
    }

    /**
     * Getter for property needsTarget.
     *
     * @return Value of property needsTarget.
     */
    public boolean isNeedsTarget() {
        return this.needsTarget;
    }
    
    /**
     * Setter for property needsTarget.
     *
     * @param needsTarget New value of property needsTarget.
     */
    public void setNeedsTarget(boolean needsTarget) {
        this.needsTarget = needsTarget;
    }
    
    /**
     * Getter for property targetMode.
     *
     * @return Value of property targetMode.
     */
    public int getTargetMode() {
        return this.targetMode;
    }
    
    /**
     * Setter for property targetMode.
     *
     * @param targetMode New value of property targetMode.
     */
    public void setTargetMode(int targetMode) {
        this.targetMode = targetMode;
    }
    
    /**
     * Getter for property senderFullname.
     *
     * @return Value of property senderFullname.
     */
    public String getSenderFullname() {
        return getMediaEmail().getFromFullname();
    }
    
    /**
     * Setter for property senderFullname.
     *
     * @param senderFullname New value of property senderFullname.
     */
    public void setSenderFullname(String senderFullname) {
        getMediaEmail().setFromFullname(senderFullname);
    }
    
    /**
     * Getter for property replyEmail.
     *
     * @return Value of property replyEmail.
     */
    public String getEmailReplytoEmail() {
        
        return this.emailReplytoEmail;
    }
    
    /**
     * Setter for property replyEmail.
     *
     * @param replyEmail New value of property replyEmail.
     */
    public void setEmailReplytoEmail(String replyEmail) {
        
        this.emailReplytoEmail = replyEmail;
    }
    
    /**
     * Getter for property replyFullname.
     *
     * @return Value of property replyFullname.
     */
    public String getEmailReplytoFullname() {
        
        return this.emailReplytoFullname;
    }

    public boolean isSearchEnabled() {
        return searchEnabled;
    }

    public void setSearchEnabled(boolean searchEnabled) {
        this.searchEnabled = searchEnabled;
    }

    public boolean isContentSearchEnabled() {
        return contentSearchEnabled;
    }

    public void setContentSearchEnabled(boolean contentSearchEnabled) {
        this.contentSearchEnabled = contentSearchEnabled;
    }

	/**
     * Setter for property replyFullname.
     *
     * @param replyFullname New value of property replyFullname.
     */
    public void setEmailReplytoFullname(String replyFullname) {
        
        this.emailReplytoFullname = replyFullname;
    }
    
    /**
     * Holds value of property emailOnepixel.
     */
    private String emailOnepixel;
    
    /**
     * Getter for property emailOnepixel.
     *
     * @return Value of property emailOnepixel.
     */
    public String getEmailOnepixel() {
        
        return this.emailOnepixel;
    }
    
    /**
     * Setter for property emailOnepixel.
     *
     * @param emailOnepixel New value of property emailOnepixel.
     */
    public void setEmailOnepixel(String emailOnepixel) {
        
        this.emailOnepixel = emailOnepixel;
    }  

    protected Map<Integer, Mediatype> mediatypes;
    /**
     * Getter for property mediatypes.
     *
     * @return Value of property mediatypes.
     */
    public Map<Integer, Mediatype> getMediatypes() {
        if (mediatypes == null) {
            mediatypes = new HashMap<>();
        }
        return mediatypes;
    }
   
    public Mediatype getMedia(int id) {
        Mediatype media = getMediatypes().get(id);

        if (media == null && id == MediaTypes.EMAIL.getMediaCode()) {
            media = (Mediatype) getWebApplicationContext().getBean("MediatypeEmail");
            mediatypes.put(id, media);
        }

        return media;
    }
 
    public MediatypeEmail getMediaEmail() {
        return (MediatypeEmail) getMedia(MediaTypes.EMAIL.getMediaCode());
    }
 
    /**
     * Setter for property mediatypes.
     *
     * @param mediatypes New value of property mediatypes.
     */
    public void setMediatypes(Map<Integer, Mediatype> mediatypes) {
        this.mediatypes = mediatypes;
    }  
    
    /**
     * Holds value of property archived.
     */
    private boolean archived = false;

    /**
     * Getter for property archived.
     * @return Value of property archived.
     */
    public boolean isArchived() {
        return this.archived;
    }

    /**
     * Setter for property archived.
     * @param archived New value of property archived.
     */
    public void setArchived(boolean archived) {
        this.archived = archived;
    }

	public List<Map<String, String>> getActions() {
		return actions;
	}

	public void setActions(List<Map<String, String>> actions) {
		this.actions = actions;
	}

	/**
     * Holds value of property mailingTypeNormal.
     */
    protected boolean mailingTypeNormal = true;
    
    /**
     * Holds value of property mailingTypeEvent.
     */
    protected boolean mailingTypeEvent;
    
    /**
     * Holds value of property mailingTypeDate.
     */
    protected boolean mailingTypeDate;
    
    /**
     * Holds value of property types.
     */
    protected String types = "";

	private boolean dynamicTemplate;

    /**
     * Getter for property mailingTypeDate.
     *
     * @return Value of property mailingTypeDate.
     */
	public boolean getMailingTypeDate() {
		return mailingTypeDate;
	}

	/**
     * Setter for property mailingTypeDate.
     *
     * @param mailingTypeDate New value of property mailingTypeDate.
     */
	public void setMailingTypeDate(boolean mailingTypeDate) {
		this.mailingTypeDate = mailingTypeDate;
	}

	/**
     * Getter for property mailingTypeEvent.
     *
     * @return Value of property mailingTypeEvent.
     */
	public boolean getMailingTypeEvent() {
		return mailingTypeEvent;
	}

	/**
     * Setter for property mailingTypeEvent.
     *
     * @param mailingTypeEvent New value of property mailingTypeEvent.
     */
	public void setMailingTypeEvent(boolean mailingTypeEvent) {
		this.mailingTypeEvent = mailingTypeEvent;
	}

	/**
     * Getter for property mailingTypeNormal.
     *
     * @return Value of property mailingTypeNormal.
     */
	public boolean getMailingTypeNormal() {
		return mailingTypeNormal;
	}

	/**
     * Setter for property mailingTypeNormal.
     *
     * @param mailingTypeNormal New value of property mailingTypeNormal.
     */
	public void setMailingTypeNormal(boolean mailingTypeNormal) {
		this.mailingTypeNormal = mailingTypeNormal;
	}
	
	public void setMessages(ActionMessages messages) {
		this.messages = messages;
	}
	
	public ActionMessages getMessages() {
		return this.messages;
	}

    /**
     * Getter for property mailingType.
     *
     * @return Value of property mailingType.
     */
	public String getTypes() {
		types = "";
		if(mailingTypeNormal) {
			types = "0";
		}
		if(mailingTypeEvent) {
			if(!types.equals("")) {
				types = types + ",";
			}
			types = types + "1";
		}
		if(mailingTypeDate) {
			if(!types.equals("")) {
				types = types + ",";
			}
			types = types + "2";
		}
		if(types.equals("")) {
			types = "100";
		}
		return types;
	}

	public int getPreviousAction() {
		return previousAction;
	}

	public void setPreviousAction(int previousAction) {
		this.previousAction = previousAction;
	}

	public int getOldMailFormat() {
		return oldMailFormat;
	}

	public void setOldMailFormat(int oldMailFormat) {
		this.oldMailFormat = oldMailFormat;
	}

	public void setErrors(ActionMessages errors) {
		this.errors = errors;
	}

	public ActionMessages getErrors() {
		return this.errors;
	}

    public List<MailingBase> getTemplateMailingBases() {
        return templateMailingBases;
    }

    public void setTemplateMailingBases(List<MailingBase> templateMailingBases) {
        this.templateMailingBases = templateMailingBases;
    }

    public String getTemplateShortname() {
        return templateShortname;
    }

    public void setTemplateShortname(String templateShortname) {
        this.templateShortname = templateShortname;
    }

    public List<Mailinglist> getMailingLists() {
        return mailingLists;
    }

    public void setMailingLists(List<Mailinglist> mailingLists) {
        this.mailingLists = mailingLists;
    }

    public List<Campaign> getCampaigns() {
        return campaigns;
    }

    public void setCampaigns(List<Campaign> campaigns) {
        this.campaigns = campaigns;
    }

    public List<TargetLight> getTargets() {
        return targets;
    }

    public void setTargets(List<TargetLight> targets) {
        this.targets = targets;
    }

    public List<TargetLight> getTargetGroupsList() {
        return targetGroupsList;
    }

    public void setTargetGroupsList(List<TargetLight> targetGroupsList) {
        this.targetGroupsList = targetGroupsList;
    }

	public void setUseDynamicTemplate(boolean dynamicTemplate) {
		this.dynamicTemplate = dynamicTemplate;
	}

	public boolean getUseDynamicTemplate() {
		return this.dynamicTemplate;
	}
	
	public void setDynamicTemplateString( String dynamicTemplateString) {
		if( dynamicTemplateString == null)
			this.dynamicTemplate = false;
		else
			this.dynamicTemplate = dynamicTemplateString.equals( "on") || dynamicTemplateString.equals( "on") || dynamicTemplateString.equals( "true");
	}

	public String getDynamicTemplateString() {
		if( dynamicTemplate)
			return "on";
		else
			return "";
	}
	
	@Override
	protected boolean isParameterExcludedForUnsafeHtmlTagCheck( String parameterName, HttpServletRequest request) {
		return parameterName.equals( "textTemplate") || parameterName.equals( "htmlTemplate");
	}

    public boolean isCanChangeEmailSettings() {
        return canChangeEmailSettings;
    }

    public void setCanChangeEmailSettings(boolean canChangeEmailSettings) {
        this.canChangeEmailSettings = canChangeEmailSettings;
    }

    public boolean isCanChangeMailinglist() {
        return canChangeMailinglist;
    }

    public void setCanChangeMailinglist(boolean canChangeMailinglist) {
        this.canChangeMailinglist = canChangeMailinglist;
    }

    public boolean isMailingEditable() {
        return mailingEditable;
    }

    public void setMailingEditable(boolean mailingEditable) {
        this.mailingEditable = mailingEditable;
    }
}
