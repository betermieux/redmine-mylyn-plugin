package net.sf.redmine_mylyn.internal.core;

import java.lang.reflect.Field;
import java.util.Date;
import java.util.List;
import java.util.Set;

import net.sf.redmine_mylyn.api.model.Attachment;
import net.sf.redmine_mylyn.api.model.Configuration;
import net.sf.redmine_mylyn.api.model.CustomField;
import net.sf.redmine_mylyn.api.model.CustomValue;
import net.sf.redmine_mylyn.api.model.Issue;
import net.sf.redmine_mylyn.api.model.Journal;
import net.sf.redmine_mylyn.api.model.TimeEntry;
import net.sf.redmine_mylyn.api.model.container.CustomValues;
import net.sf.redmine_mylyn.core.IRedmineConstants;
import net.sf.redmine_mylyn.core.RedmineAttribute;
import net.sf.redmine_mylyn.core.RedmineCorePlugin;
import net.sf.redmine_mylyn.core.RedmineRepositoryConnector;
import net.sf.redmine_mylyn.core.RedmineUtil;
import net.sf.redmine_mylyn.core.RedmineTaskTimeEntryMapper;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.mylyn.commons.core.StatusHandler;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.mylyn.tasks.core.data.TaskAttachmentMapper;
import org.eclipse.mylyn.tasks.core.data.TaskAttribute;
import org.eclipse.mylyn.tasks.core.data.TaskCommentMapper;
import org.eclipse.mylyn.tasks.core.data.TaskData;

public class IssueMapper {

	public static void updateTaskData(TaskRepository repository, TaskData taskData, Configuration cfg, Issue issue) throws CoreException {

		TaskAttribute root = taskData.getRoot();
		TaskAttribute taskAttribute = null;
		
		/* Default Attributes */
		for (RedmineAttribute redmineAttribute : RedmineAttribute.values()) {
			Field field = redmineAttribute.getAttributeField();
			if(field!=null) {
				taskAttribute = root.getAttribute(redmineAttribute.getTaskKey());
				if(taskAttribute !=null ) {
					try {
						setValue(taskAttribute, field.get(issue));
					} catch (Exception e) {
						IStatus status = RedmineCorePlugin.toStatus(e, "Should never happens");
						StatusHandler.fail(status);
					}
				}
			}
		}

		/* Custom Attributes */
		if(issue.getCustomValues()!=null) {
			for (CustomValue customValue : issue.getCustomValues().getAll()) {
				taskAttribute = taskData.getRoot().getAttribute(IRedmineConstants.TASK_KEY_PREFIX_ISSUE_CF + customValue.getCustomFieldId());
				if(taskAttribute!=null) {
					setValue(taskAttribute, customValue.getValue());
				}
			}
		}
		
		/* Journals */
		if(issue.getJournals()!=null) {
			int jrnlCount=1;
			for (Journal journal : issue.getJournals().getAll()) {
				TaskCommentMapper mapper = new TaskCommentMapper();
				mapper.setAuthor(repository.createPerson(""+journal.getUserId()));
				mapper.setCreationDate(journal.getCreatedOn());
				mapper.setText(journal.getNotes());
				String issueUrl = RedmineRepositoryConnector.getTaskUrl(repository.getUrl(), issue.getId());
				mapper.setUrl(issueUrl + String.format(IRedmineConstants.REDMINE_URL_PART_COMMENT, journal.getId()));
				mapper.setNumber(jrnlCount++);
				mapper.setCommentId(String.valueOf(journal.getId()));
				
				taskAttribute = taskData.getRoot().createAttribute(TaskAttribute.PREFIX_COMMENT + mapper.getCommentId());
				mapper.applyTo(taskAttribute);
			}
		}

		/* Attachments */
		if(issue.getAttachments()!=null) {
			for (Attachment	attachment : issue.getAttachments().getAll()) {
				TaskAttachmentMapper mapper = new TaskAttachmentMapper();
				mapper.setAttachmentId("" + attachment.getId()); //$NON-NLS-1$
				mapper.setAuthor(repository.createPerson(""+attachment.getAuthorId()));
				mapper.setDescription(attachment.getDescription());
				mapper.setCreationDate(attachment.getCreatedOn());
				mapper.setContentType(attachment.getContentType());
				mapper.setFileName(attachment.getFilename());
				mapper.setLength((long)attachment.getFilesize());
				mapper.setUrl(String.format(IRedmineConstants.REDMINE_URL_ATTACHMENT_DOWNLOAD, repository.getUrl(), attachment.getId()));
				
				taskAttribute = taskData.getRoot().createAttribute(TaskAttribute.PREFIX_ATTACHMENT + mapper.getAttachmentId());
				mapper.applyTo(taskAttribute);
			}
		}
		
		//TODO
		if(true /*ticket.getRight(RedmineAcl.TIMEENTRY_VIEW*/) {
			//TODO kind/label
			taskAttribute = taskData.getRoot().createAttribute(IRedmineConstants.TASK_ATTRIBUTE_TIMEENTRY_TOTAL);
			
			if(issue.getTimeEntries()!=null) {
				taskAttribute.setValue(""+issue.getTimeEntries().getSum());
				
				
				for (TimeEntry timeEntry : issue.getTimeEntries().getAll()) {
					RedmineTaskTimeEntryMapper mapper = new RedmineTaskTimeEntryMapper();
					mapper.setTimeEntryId(timeEntry.getId());
					mapper.setUser(repository.createPerson(""+timeEntry.getUserId()));
					mapper.setActivityId(timeEntry.getActivityId());
					mapper.setHours(timeEntry.getHours());
					mapper.setSpentOn(timeEntry.getSpentOn());
					mapper.setComments(timeEntry.getComments());
					mapper.setCustomValues(timeEntry.getCustomValues().getAll());
					
					taskAttribute = taskData.getRoot().createAttribute(IRedmineConstants.TASK_ATTRIBUTE_TIMEENTRY_PREFIX + mapper.getTimeEntryId());
					mapper.applyTo(taskAttribute, cfg);
				}
			}
			
		}
	}

	public static Issue createIssue(TaskRepository repository, TaskData taskData, Set<TaskAttribute> oldAttributes, Configuration cfg) throws CoreException {
		Issue issue = taskData.getTaskId().isEmpty() ? new Issue() : new Issue(RedmineUtil.parseIntegerId(taskData.getTaskId()));

		TaskAttribute root = taskData.getRoot();
		TaskAttribute taskAttribute = null;
		
		/* Default Attributes */
		for (RedmineAttribute redmineAttribute : RedmineAttribute.values()) {
			Field field = redmineAttribute.getAttributeField();
			if(!redmineAttribute.isOperationValue() && field!=null) {
				taskAttribute = root.getAttribute(redmineAttribute.getTaskKey());
				if(taskAttribute !=null ) {
					try {
						switch(redmineAttribute) {
						case SUMMARY:
						case DESCRIPTION:
						case COMMENT: field.set(issue, taskAttribute.getValue()); break;
						case PROGRESS:field.setInt(issue, taskAttribute.getValue().isEmpty() ? 0 : Integer.parseInt(taskAttribute.getValue())); break;
						case ESTIMATED:field.setFloat(issue, Float.parseFloat(taskAttribute.getValue())); break;
						default:
							if(redmineAttribute.getType().equals(TaskAttribute.TYPE_SINGLE_SELECT) || redmineAttribute.getType().equals(TaskAttribute.TYPE_PERSON) || redmineAttribute==RedmineAttribute.PARENT) {
								int idVal = RedmineUtil.parseIntegerId(taskAttribute.getValue());
								if(idVal>0) {
									field.setInt(issue, idVal);
								}
							} else
							
							if(redmineAttribute.getType().equals(TaskAttribute.TYPE_DATE)) {
								if (!taskAttribute.getValue().isEmpty()) {
									field.set(issue, RedmineUtil.parseDate(taskAttribute.getValue()));
								}
							}
						}
					} catch (Exception e) {
						IStatus status = RedmineCorePlugin.toStatus(e, "Should never happens");
						StatusHandler.fail(status);
					}
				}
			}
		}

		/* Custom Attributes */
		int[] customFieldIds = cfg.getProjects().getById(issue.getProjectId()).getCustomFieldIdsByTrackerId(issue.getTrackerId());
		if(customFieldIds!=null && customFieldIds.length>0) {
			CustomValues customValues = new CustomValues();
			issue.setCustomValues(customValues);
			for (int customFieldId : customFieldIds) {
				taskAttribute = root.getAttribute(IRedmineConstants.TASK_KEY_PREFIX_ISSUE_CF + customFieldId);
				if(taskAttribute!=null) {
					customValues.setCustomValue(customFieldId, formatCustomValue(taskAttribute.getValue(), customFieldId, cfg));
				}
			}
		}
		
		//TODO Operation
		
		return issue;
	}

	public static TimeEntry createTimeEntry(TaskRepository repository, TaskData taskData, Set<TaskAttribute> oldAttributes, Configuration cfg) throws CoreException {
		TimeEntry timeEntry = null;
		
		try {
			String val = getValue(taskData, RedmineAttribute.TIME_ENTRY_HOURS);
			if(!val.isEmpty()) {
				timeEntry = new TimeEntry();
				
				/* Default Attributes */
				timeEntry.setHours(Float.parseFloat(val));
				timeEntry.setActivityId(Integer.parseInt(getValue(taskData, RedmineAttribute.TIME_ENTRY_ACTIVITY)));
				timeEntry.setComments(getValue(taskData, RedmineAttribute.TIME_ENTRY_COMMENTS));
				
				/* Custom Attributes */
				List<CustomField> customFields = cfg.getCustomFields().getTimeEntryCustomFields();
				if(customFields!=null && customFields.size()>0) {
					CustomValues customValues = new CustomValues();
					timeEntry.setCustomValues(customValues);
					for (CustomField customField : customFields) {
						String value = getValue(taskData, IRedmineConstants.TASK_KEY_PREFIX_TIMEENTRY_CF+customField.getId());
						customValues.setCustomValue(customField.getId(), formatCustomValue(value, customField.getId(), cfg));
					}
				}
			}
		} catch (NumberFormatException e) {
			timeEntry = null;
			IStatus status = RedmineCorePlugin.toStatus(e, "Illegal Attribute Value");
			StatusHandler.log(status);
		}
		
		return timeEntry;
	}
	
	private static String formatCustomValue(String value, int customFieldId, Configuration cfg) {
		CustomField field = cfg.getCustomFields().getById(customFieldId);
		if(field!=null) {
			switch(field.getFieldFormat()) {
			case DATE: value = value.isEmpty() ? "" : RedmineUtil.formatDate(RedmineUtil.parseDate(value)); break;
			case BOOL: value = Boolean.parseBoolean(value) ? "1" : "0";
			}
		}
		return value;
	}
	
	private static String getValue(TaskData taskData, RedmineAttribute redmineAttribute) {
		return getValue(taskData, redmineAttribute.getTaskKey());
	}
	
	private static String getValue(TaskData taskData, String taskKey) {
		TaskAttribute attribute = taskData.getRoot().getAttribute(taskKey);
		return attribute==null ? "" : attribute.getValue();
	}
	
	private static void setValue(TaskAttribute attribute, Object value) {
		if(value==null) {
			setValue(attribute, "");
		} else if(value instanceof String) {
			setValue(attribute, (String)value);
		} else if(value instanceof Date) {
			setValue(attribute, (Date)value);
		} else if(value instanceof Integer) {
			setValue(attribute, ((Integer)value).intValue());
		} else {
			setValue(attribute, value.toString());
		}
	}

	private static void setValue(TaskAttribute attribute, String value) {
		if(value==null) {
			attribute.setValue("");
		} else if(attribute.getMetaData().getType().equals(TaskAttribute.TYPE_BOOLEAN)) {
			attribute.setValue(RedmineUtil.parseBoolean(value).toString());
		} else if(attribute.getMetaData().getType().equals(TaskAttribute.TYPE_DATE) || attribute.getMetaData().getType().equals(TaskAttribute.TYPE_DATETIME)) {
			setValue(attribute, RedmineUtil.parseRedmineDate(value));
		} else {
			attribute.setValue(value);
		}

	}

	private static void setValue(TaskAttribute attribute, Date value) {
		if(value==null) {
			attribute.setValue("");
		} else {
			attribute.setValue(""+value.getTime());
		}
	}
	
	private static void setValue(TaskAttribute attribute, int value) {
		if((attribute.getMetaData().getType().equals(TaskAttribute.TYPE_SINGLE_SELECT) || attribute.getId().equals(RedmineAttribute.PARENT.getTaskKey())) && value<1) {
			attribute.setValue("");
		} else {
			attribute.setValue(""+value);
		}
	}
}