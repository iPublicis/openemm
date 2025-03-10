/*

    Copyright (C) 2019 AGNITAS AG (https://www.agnitas.org)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

*/

package com.agnitas.emm.core.target.eql.emm.querybuilder;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

import antlr.collections.List;
import net.sf.json.JSONObject;
import net.sf.json.JsonConfig;
import net.sf.json.util.NewBeanInstanceStrategy;

public class DefaultQueryBuilderJsonConfig extends JsonConfig {

	private static final class QueryBuilderNewBeanInstanceStrategy extends NewBeanInstanceStrategy {

		@Override
		public final Object newInstance(@SuppressWarnings("rawtypes") final Class beanClass, final JSONObject jsonObject) throws InstantiationException, IllegalAccessException, SecurityException, NoSuchMethodException, InvocationTargetException {
			if(List.class.isAssignableFrom(beanClass)) { 
				return new QueryBuilderGroupNode();
			} else {
				if(jsonObject.optString("condition", null) != null) {
					return new QueryBuilderGroupNode();
				} else {
					return new QueryBuilderRuleNode();
				}
			}
		}
	}

	public DefaultQueryBuilderJsonConfig() {
		setRootClass(QueryBuilderGroupNode.class);
		setNewBeanInstanceStrategy(new QueryBuilderNewBeanInstanceStrategy());
		setClassMap(createClassMap());
	}
	
	private static Map<String, Class<?>> createClassMap() {
		final Map<String, Class<?>> map = new HashMap<>();
		map.put("rules", QueryBuilderBaseNode.class);
		return map;
	}
}
