/*

    Copyright (C) 2019 AGNITAS AG (https://www.agnitas.org)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

*/

package com.agnitas.emm.core.mailinglist.form;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import com.agnitas.emm.core.birtstatistics.monthly.dto.RecipientStatisticDto;

public class MailinglistForm {
	
	private int id;
	
	@NotNull(message = "error.name.is.empty")
	@Size(min = 3, message = "error.name.too.short")
	private String shortname;
	
	private String description;
	
	private int targetId;
	
	private RecipientStatisticDto statistic;
	
	public int getId() {
		return id;
	}
	
	public void setId(int id) {
		this.id = id;
	}
	
	public String getShortname() {
		return shortname;
	}
	
	public void setShortname(String shortname) {
		this.shortname = shortname;
	}
	
	public String getDescription() {
		return description;
	}
	
	public void setDescription(String description) {
		this.description = description;
	}
	
	public int getTargetId() {
		return targetId;
	}
	
	public void setTargetId(int targetId) {
		this.targetId = targetId;
	}
	
	public RecipientStatisticDto getStatistic() {
        return statistic;
    }
    
    public void setStatistic(RecipientStatisticDto statistic) {
        this.statistic = statistic;
    }
}
