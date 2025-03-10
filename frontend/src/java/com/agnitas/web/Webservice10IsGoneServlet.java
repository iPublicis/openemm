/*

    Copyright (C) 2019 AGNITAS AG (https://www.agnitas.org)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

*/

package com.agnitas.web;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

/**
 * This servlet show a http error code 410 for every kind of request, to show that Webservice 1.0 is no longer available.
 */
public class Webservice10IsGoneServlet extends HttpServlet {
	private static final long serialVersionUID = -6055323317524093724L;

	@SuppressWarnings("unused")
	private static final transient Logger logger = Logger.getLogger(Webservice10IsGoneServlet.class);

	/**
	 * Always shows a http error code 410
	 */
	@Override
	public void service(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
		response.sendError(HttpServletResponse.SC_GONE, "Webservice 1.0 is no longer available");
	}
}
