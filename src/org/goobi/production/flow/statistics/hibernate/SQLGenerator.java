/*
 * This file is part of the Goobi Application - a Workflow tool for the support of
 * mass digitization.
 *
 * Visit the websites for more information.
 *     - http://gdz.sub.uni-goettingen.de
 *     - http://www.goobi.org
 *     - http://launchpad.net/goobi-production
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details. You
 * should have received a copy of the GNU General Public License along with this
 * program; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */

package org.goobi.production.flow.statistics.hibernate;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

import org.goobi.production.flow.statistics.enums.TimeUnit;

/**
 * 
 * This is the superclass for SQL generation and it provides some common
 * data collection in the constructor and abstract methods which needs to 
 * be implemented in the sub classes (  
 * 
 * 
 * @author Wulf Riebensahm
 *
 */

public abstract class SQLGenerator {

	String mySql = null;
	Date myTimeFrom = null;
	Date myTimeTo = null;
	TimeUnit myTimeUnit = null;
	String myIdsCondition = null;
	String myIdFieldName = "prozesse.prozesseid";
	List<Integer> ids;
	private SQLGenerator() {
		super();
	}

	public SQLGenerator(Date timeFrom, Date timeTo, TimeUnit timeUnit,
			List<Integer> ids, String idFieldName) {
		this();
		myTimeFrom = timeFrom;
		myTimeTo = timeTo;
		myTimeUnit = timeUnit;
		this.ids = ids;
		if (idFieldName != null) {
			this.myIdFieldName = idFieldName;
		}

		// if ids are passed on build a where clause about ids
		// this will be a condition of the inner table
		conditionGeneration();

	}

	private void conditionGeneration() {
		if (ids != null) {
			myIdsCondition = myIdFieldName + " in (";
			for (Integer i : ids) {
				myIdsCondition = myIdsCondition.concat(i.toString() + ",");
			}
			myIdsCondition = myIdsCondition.substring(0, myIdsCondition
					.length()
					- ",".length())
					+ ")";
		}
	}

	/************************************************************************
	 * get actual SQL Query as String. Depends on the done step of process.
	 * 
	 * @return String - SQL Query as String
	 ***********************************************************************/
	public abstract String getSQL();

	/*****************************************************************
	 * generates SQL-WHERE for the time frame 
	 * 
	 * @param timeFrom start time 
	 * @param timeTo   end time
	 * @param timeLimiter name of field used to apply the timeframe
	 ******************************************************************/
	protected static String getWhereClauseForTimeFrame(Date timeFrom,
			Date timeTo, String timeLimiter) {

		if (timeFrom == null && timeTo == null) {
			return "";
		}

		if (timeFrom != null && timeTo != null) {
			return " date_format(" + timeLimiter
					+ ",'%Y%m%d%H%i%s')+0>=date_format('"
					+ dateToSqlTimestamp(timeFrom) + "','%Y%m%d%H%i%s')+0 AND "
					+ " date_format(" + timeLimiter
					+ ",'%Y%m%d%H%i%s')+0<=date_format('"
					+ dateToSqlTimestamp(timeTo) + "','%Y%m%d%H%i%s')+0 ";
		}

		if (timeFrom != null) {
			return " date_format(" + timeLimiter
					+ ",'%Y%m%d%H%i%s')+0>=date_format('"
					+ dateToSqlTimestamp(timeFrom) + "','%Y%m%d%H%i%s')+0";
		}

		if (timeTo != null) {
			return " date_format(" + timeLimiter
					+ ",'%Y%m%d%H%i%s')+0<=date_format('"
					+ dateToSqlTimestamp(timeTo) + "','%Y%m%d%H%i%s')+0";
		}
		return "";

	}

	/*****************************************************************
	 * generates time format from {@link TimeUnit}
	 * 
	 * @param fieldExpression  
	 * @param timeUnit
	 * @return String - simple date format 
	 ******************************************************************/
	protected static String getIntervallExpression(TimeUnit timeUnit,
			String fieldExpression) {

		if (timeUnit == null) {
			return "'Total'";
		}

		switch (timeUnit) {

		case years:
			return "year(" + fieldExpression + ")";

		case months:
			return "concat(year(" + fieldExpression + ") , '/' , date_format("
					+ fieldExpression + ",'%m'))";

		case quarters:
			return "concat(year(" + fieldExpression + ") , '/' , quarter("
					+ fieldExpression + "))";

		case weeks:
			return "concat(left(yearweek(" + fieldExpression
					+ ",3),4), '/', right(yearweek(" + fieldExpression
					+ ",3),2))";

		case days:
			return "concat(year(" + fieldExpression + ") , '-' , date_format("
					+ fieldExpression + ",'%m') , '-' , date_format("
					+ fieldExpression + ",'%d'))";

		default:
			return "'timeUnit(" + timeUnit.getTitle() + ") undefined'";

		}
	}

	/**
	 *  converts the format of a date to match MySQL Timestamp format
	 * @param date
	 * @return 
	 */
	private static Timestamp dateToSqlTimestamp(Date date) {
		Timestamp timestamp = new Timestamp(date.getTime());
		return timestamp;
	}
	
	public void setMyIdFieldName(String name) {
		myIdFieldName = name;
		conditionGeneration();
	}
	

}