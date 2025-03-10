/*

    Copyright (C) 2019 AGNITAS AG (https://www.agnitas.org)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

*/

package com.agnitas.emm.core.workflow.service.util;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import org.agnitas.target.TargetNode;
import org.agnitas.target.TargetOperator;
import org.agnitas.util.DateUtilities;
import org.agnitas.util.DbColumnType;

import com.agnitas.beans.ComMailing;
import com.agnitas.emm.core.workflow.beans.WorkflowConnection;
import com.agnitas.emm.core.workflow.beans.WorkflowDeadline;
import com.agnitas.emm.core.workflow.beans.WorkflowDeadline.WorkflowDeadlineTimeUnit;
import com.agnitas.emm.core.workflow.beans.WorkflowDecision;
import com.agnitas.emm.core.workflow.beans.WorkflowIcon;
import com.agnitas.emm.core.workflow.beans.WorkflowIconType;
import com.agnitas.emm.core.workflow.beans.WorkflowMailingAware;
import com.agnitas.emm.core.workflow.beans.WorkflowReactionType;
import com.agnitas.emm.core.workflow.beans.WorkflowStart;
import com.agnitas.emm.core.workflow.beans.WorkflowStart.WorkflowStartEventType;
import com.agnitas.emm.core.workflow.beans.WorkflowStart.WorkflowStartType;
import com.agnitas.emm.core.workflow.beans.WorkflowStartStop;

public class WorkflowUtils {
	public static final int GCD_ACCURACY = 10;
	
	public static final String WORKFLOW_TARGET_NAME_PATTERN = "[campaign target: %s]";
	public static final String WORKFLOW_TARGET_NAME_SQL_PATTERN = "[campaign target: %]";

	public static Map<TargetOperator, String> getOperatorTypeSupportMap() {
		Map<TargetOperator, String> map = new HashMap<>();

		map.put(TargetNode.OPERATOR_EQ, "*");
		map.put(TargetNode.OPERATOR_NEQ, "*");
		map.put(TargetNode.OPERATOR_GT, "*");
		map.put(TargetNode.OPERATOR_LT, "*");
		map.put(TargetNode.OPERATOR_LT_EQ, "*");
		map.put(TargetNode.OPERATOR_GT_EQ, "*");
		map.put(TargetNode.OPERATOR_IS, "*");
		map.put(TargetNode.OPERATOR_MOD, DbColumnType.GENERIC_TYPE_INTEGER + "," + DbColumnType.GENERIC_TYPE_DOUBLE);
		map.put(TargetNode.OPERATOR_LIKE, DbColumnType.GENERIC_TYPE_VARCHAR + "," + DbColumnType.GENERIC_TYPE_CHAR);
		map.put(TargetNode.OPERATOR_NLIKE, DbColumnType.GENERIC_TYPE_VARCHAR + "," + DbColumnType.GENERIC_TYPE_CHAR);
		map.put(TargetNode.OPERATOR_CONTAINS, DbColumnType.GENERIC_TYPE_VARCHAR + "," + DbColumnType.GENERIC_TYPE_CHAR);
		map.put(TargetNode.OPERATOR_NOT_CONTAINS, DbColumnType.GENERIC_TYPE_VARCHAR + "," + DbColumnType.GENERIC_TYPE_CHAR);
		map.put(TargetNode.OPERATOR_STARTS_WITH, DbColumnType.GENERIC_TYPE_VARCHAR + "," + DbColumnType.GENERIC_TYPE_CHAR);
		map.put(TargetNode.OPERATOR_NOT_STARTS_WITH, DbColumnType.GENERIC_TYPE_VARCHAR + "," + DbColumnType.GENERIC_TYPE_CHAR);

		return Collections.unmodifiableMap(map);
	}

	public static Double calculateGCD(List<Double> parts) {
		Iterator<Double> iterator = parts.iterator();
		Double value;

		if (iterator.hasNext()) {
			value = iterator.next();
		} else {
			return null;
		}

		while (iterator.hasNext()) {
			value = calculateGCD(value, iterator.next());
		}

		return value;
	}

	public static double roundGCD(double value) {
		return (double) Math.round(value * GCD_ACCURACY) / GCD_ACCURACY;
	}

	private static double calculateGCD(double a, double b) {
		if (a == b) {
			return roundGCD(a);
		}

		if (a < b) {
			if (a * GCD_ACCURACY < 1) {
				return roundGCD(b);
			}
			return calculateGCD(a, b % a);
		} else { // b < a
			if (b * GCD_ACCURACY < 1) {
				return roundGCD(a);
			}
			return calculateGCD(b, a % b);
		}
	}

	public static boolean is(WorkflowStart start, WorkflowStartType type) {
		Objects.requireNonNull(type);

		return start.isFilled() && start.getStartType() == type;
	}

	public static boolean is(WorkflowStart start, WorkflowStartEventType event) {
		Objects.requireNonNull(event);

		return is(start, WorkflowStartType.EVENT) && start.getEvent() == event;
	}

	public static boolean is(WorkflowStart start, WorkflowReactionType reaction) {
		Objects.requireNonNull(reaction);

		return is(start, WorkflowStartEventType.EVENT_REACTION) && start.getReaction() == reaction;
	}

	public static double doubleTo2Digits(Double part) {
		BigDecimal bd = new BigDecimal(part);
		bd = bd.setScale(2, BigDecimal.ROUND_HALF_UP);
		return bd.doubleValue();
	}

	public static Date getStartStopIconDate(WorkflowStartStop icon, TimeZone timezone) {
		Date date = icon.getDate();

		if (date == null) {
			return null;
		} else {
			Calendar calendar = DateUtilities.calendar(date, timezone);
			calendar.set(Calendar.HOUR_OF_DAY, icon.getHour());
			calendar.set(Calendar.MINUTE, icon.getMinute());
			return calendar.getTime();
		}
	}

	public static Date getReminderSpecificDate(WorkflowStartStop icon, TimeZone timezone) {
		Date date = icon.getRemindDate();

		if (date == null || !icon.isRemindSpecificDate()) {
			return null;
		} else {
			Calendar calendar = DateUtilities.calendar(date, timezone);
			calendar.set(Calendar.HOUR_OF_DAY, icon.getRemindHour());
			calendar.set(Calendar.MINUTE, icon.getRemindMinute());
			return calendar.getTime();
		}
	}

	public static int getMailingId(WorkflowIcon icon) {
		if (icon instanceof  WorkflowMailingAware) {
			return ((WorkflowMailingAware) icon).getMailingId();
		} else {
			return 0;
		}
	}

    public static boolean isMailingIcon(WorkflowIcon icon) {
        return icon.getType() == WorkflowIconType.ACTION_BASED_MAILING.getId() ||
                icon.getType() == WorkflowIconType.MAILING.getId() ||
                icon.getType() == WorkflowIconType.DATE_BASED_MAILING.getId() ||
                icon.getType() == WorkflowIconType.FOLLOWUP_MAILING.getId();
    }

    public static boolean isStartStopIcon(WorkflowIcon icon) {
        return icon.getType() == WorkflowIconType.START.getId() || icon.getType() == WorkflowIconType.STOP.getId();
    }

	public static boolean isAutoOptimizationIcon(WorkflowIcon icon) {
		if (icon.getType() == WorkflowIconType.DECISION.getId()) {
			WorkflowDecision decision = (WorkflowDecision) icon;
			return decision.getDecisionType() == WorkflowDecision.WorkflowDecisionType.TYPE_AUTO_OPTIMIZATION;
		}
		return false;
	}

	public static boolean isBranchingDecisionIcon(WorkflowIcon icon) {
		if (icon.getType() == WorkflowIconType.DECISION.getId()) {
			WorkflowDecision decision = (WorkflowDecision) icon;
			return decision.getDecisionType() == WorkflowDecision.WorkflowDecisionType.TYPE_DECISION;
		}
		return false;
	}
	
	public static boolean isReactionCriteriaDecision(WorkflowDecision decision) {
		return decision.getDecisionCriteria() == WorkflowDecision.WorkflowDecisionCriteria.DECISION_REACTION;
	}
	
	public static boolean isProfileFieldCriteriaDecision(WorkflowDecision decision) {
		return decision.getDecisionCriteria() == WorkflowDecision.WorkflowDecisionCriteria.DECISION_PROFILE_FIELD;
	}

	public static Deadline asDeadline(WorkflowStart start, TimeZone timezone) {
		return asDeadline(start.getDate(), start.getHour(), start.getMinute(), timezone);
	}

	public static Deadline asDeadline(WorkflowDeadline deadline, TimeZone timezone) {
		switch (deadline.getDeadlineType()) {
			case TYPE_DELAY:
				return asDeadline(deadline.getTimeUnit(), deadline.getDelayValue());

			case TYPE_FIXED_DEADLINE:
				return asDeadline(deadline.getDate(), deadline.getHour(), deadline.getMinute(), timezone);

			default:
				throw new UnsupportedOperationException("Unsupported deadline type");
		}
	}

	public static String getFollowUpMethod(WorkflowReactionType reactionType) {
		if (reactionType == null) {
			return null;
		}

		switch (reactionType) {
			case CLICKED:
				return ComMailing.TYPE_FOLLOWUP_CLICKER;

			case NOT_CLICKED:
				return ComMailing.TYPE_FOLLOWUP_NON_CLICKER;

			case OPENED:
				return ComMailing.TYPE_FOLLOWUP_OPENER;

			case NOT_OPENED:
				return ComMailing.TYPE_FOLLOWUP_NON_OPENER;

			// FIXME: Not supported as followup methods.
			case BOUGHT:
			case NOT_BOUGHT:
			default:
				return null;
		}
	}

	public static void forEachConnection(List<WorkflowIcon> icons, BiConsumer<Integer, Integer> consumer) {
		for (WorkflowIcon icon : icons) {
			List<WorkflowConnection> connections = icon.getConnections();
			if (connections != null) {
				for (WorkflowConnection connection : connections) {
					consumer.accept(icon.getId(), connection.getTargetIconId());
				}
			}
		}
	}

	private static Deadline asDeadline(WorkflowDeadlineTimeUnit deadlineTimeUnit, int value) {
		switch (deadlineTimeUnit) {
			case TIME_UNIT_MINUTE:
				return new Deadline(TimeUnit.MINUTES.toMillis(value));
			case TIME_UNIT_HOUR:
				return new Deadline(TimeUnit.HOURS.toMillis(value));
			case TIME_UNIT_DAY:
				return new Deadline(TimeUnit.DAYS.toMillis(value));
			case TIME_UNIT_WEEK:
				return new Deadline(TimeUnit.DAYS.toMillis(value * 7));
			case TIME_UNIT_MONTH:
				return new Deadline(TimeUnit.DAYS.toMillis(value * 30));
			default:
				throw new UnsupportedOperationException("Unsupported relative deadline time unit");
		}
	}

	private static Deadline asDeadline(Date date, int hours, int minutes, TimeZone timezone) {
		return new Deadline(DateUtilities.merge(date, hours, minutes, timezone));
	}

	public static final class Deadline {
		public static boolean equals(Deadline d1, Deadline d2) {
			if (d1 == d2) {
				return true;
			}
			if (d1 == null || d2 == null) {
				return false;
			}
			return d1.equals(d2);
		}

		public static Date toDate(Date base, Deadline deadline, TimeZone timezone) {
			if (deadline.isRelative()) {
				return toDate(base, deadline.getValue(), deadline.getHours(), deadline.getMinutes(), timezone);
			} else {
				return new Date(deadline.getValue());
			}
		}

		private static Date toDate(Date base, long ms, int hours, int minutes, TimeZone timezone) {
			Calendar calendar = Calendar.getInstance(timezone);
			calendar.setTimeInMillis(base.getTime() + ms);

			if (hours != -1) {
				calendar.set(Calendar.HOUR_OF_DAY, hours);
			}

			if (minutes != -1) {
				calendar.set(Calendar.MINUTE, minutes);
			}

			return calendar.getTime();
		}

		private boolean isRelative;
		private long ms;
		private int hours;
		private int minutes;

		public Deadline() {
			this(true, 0, -1, -1);
		}

		public Deadline(long relativeDeadline) {
			this(true, relativeDeadline, -1, -1);
		}

		public Deadline(long relativeDeadline, int hours, int minutes) {
			this(true, relativeDeadline, hours, minutes);
		}

		public Deadline(Date deadline) {
			this(false, deadline.getTime(), -1, -1);
		}

		public Deadline(Deadline deadline) {
			this(deadline.isRelative(), deadline.getValue(), deadline.getHours(), deadline.getMinutes());
		}

		private Deadline(boolean isRelative, long ms, int hours, int minutes) {
			this.isRelative = isRelative;
			this.ms = ms;

			if (isRelative) {
				this.hours = (hours >= 0 && hours < 24) ? hours : -1;
				this.minutes = (minutes >= 0 && minutes < 60) ? minutes : -1;
			} else {
				this.hours = -1;
				this.minutes = -1;
			}
		}

		public boolean isRelative() {
			return isRelative;
		}

		public long getValue() {
			return ms;
		}

		public int getHours() {
			return hours;
		}

		public int getMinutes() {
			return minutes;
		}

		public Deadline add(Deadline deadline) {
			if (deadline.isRelative()) {
				int hours = deadline.getHours();
				int minutes = deadline.getMinutes();

				if (hours == -1 && minutes == -1) {
					hours = this.hours;
					minutes = this.minutes;
				}

				return new Deadline(isRelative, ms + deadline.getValue(), hours, minutes);
			} else if (isRelative) {
				return new Deadline(deadline);
			} else {
				return new Deadline(false, Math.max(ms, deadline.getValue()), -1, -1);
			}
		}

		@Override
		public int hashCode() {
			return (Boolean.toString(isRelative) + ms + "@" + hours + "@" + minutes).hashCode();
		}

		@Override
		public boolean equals(Object o) {
			if (o == null) {
				return false;
			}

			if (Deadline.class == o.getClass()) {
				Deadline deadline = (Deadline) o;
				if (isRelative == deadline.isRelative() && ms == deadline.getValue()) {
					return hours == deadline.getHours() && minutes == deadline.getMinutes();
				}
			}

			return false;
		}
	}

	public enum StartType {
		// Start icon is absent or a start type is invalid
		UNKNOWN,
		// Normal and follow-up mailings
		REGULAR,
		// Action-based mailings
		REACTION,
		// Date-based (rule-based) mailings
		RULE;

		public static StartType of(WorkflowStart start) {
			if (start.getStartType() == null) {
				return UNKNOWN;
			}

			switch (start.getStartType()) {
			case DATE:
				return REGULAR;

			case EVENT:
				switch (start.getEvent()) {
				case EVENT_REACTION:
					return REACTION;

				case EVENT_DATE:
					return RULE;

				default:
					return UNKNOWN;
				}

				//$FALL-THROUGH$ - should never happen
			default:
				return UNKNOWN;
			}
		}
	}
}
