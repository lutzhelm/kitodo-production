
<%@ page session="false" contentType="text/html;charset=utf-8"%>
<%@ taglib uri="http://java.sun.com/jsf/core" prefix="f"%>
<%@ taglib uri="http://java.sun.com/jsf/html" prefix="h"%>
<%@ taglib uri="http://jsftutorials.net/htmLib" prefix="htm"%>
<%@ taglib uri="http://myfaces.apache.org/tomahawk" prefix="x"%>
<%-- 
 * This file is part of the Goobi Application - a Workflow tool for the support of mass digitization.
 * 
 * Visit the websites for more information. 
 *     		- http://www.goobi.org
 *     		- http://launchpad.net/goobi-production
 * 		    - http://gdz.sub.uni-goettingen.de
 * 			- http://www.intranda.com
 * 			- http://digiverso.com 
 * 
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation, Inc., 59
 * Temple Place, Suite 330, Boston, MA 02111-1307 USA
 * 
 * Linking this library statically or dynamically with other modules is making a combined work based on this library. Thus, the terms and conditions
 * of the GNU General Public License cover the whole combination. As a special exception, the copyright holders of this library give you permission to
 * link this library with independent modules to produce an executable, regardless of the license terms of these independent modules, and to copy and
 * distribute the resulting executable under terms of your choice, provided that you also meet, for each linked independent module, the terms and
 * conditions of the license of that module. An independent module is a module which is not derived from or based on this library. If you modify this
 * library, you may extend this exception to your version of the library, but you are not obliged to do so. If you do not wish to do so, delete this
 * exception statement from your version.
--%>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">

<html>
<f:view locale="#{SpracheForm.locale}">

	<%@include file="/newpages/inc/head.jsp"%>

	<script type="text/javascript">

	function checkFrameLoad(){

		var testObject = rechts.document.getElementById("metadatenRechts");
		if(testObject!=null){
		}else{
			rechts.location.href=rechts.location.href;
		}
		testObject = oben.document.getElementById("formularOben");
		if (testObject!=null){
		}else{
			oben.location.href=oben.location.href;
		}
		
		testObject = links.document.getElementById("treeform:tabelle");
		if (testObject!=null){
		}else{
			links.location.href = links.location.href;
		}
	}
			
	</script>

	<frameset rows="59px,*" bordercolor="#003399" onload="setTimeout('checkFrameLoad()',100);">
		<frame name="oben" src="../pages/Metadaten2oben.jsf" scrolling="no" />
		<frameset cols="210px,*" bordercolor="#003399">
			<frame name="links" src="../pages/Metadaten3links.jsf"
				scrolling="auto" />
			<frame name="rechts" src="../pages/Metadaten2rechts.jsf" />
		</frameset>
	</frameset>
</f:view>
</html>
