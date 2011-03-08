<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>
<%@ taglib uri="/WEB-INF/struts-tiles.tld" prefix="tiles" %>
<%@ taglib tagdir="/WEB-INF/tags" prefix="im"%>
<%@ taglib uri="http://flymine.org/imutil" prefix="imutil" %>

<tiles:importAttribute name="mapOfInlineLists" ignore="false" />
<tiles:importAttribute name="placement" />

<c:forEach items="${mapOfInlineLists}" var="lists">
  <c:if test="${lists.key == placement}">
    <c:forEach items="${lists.value}" var="list" varStatus="status">
      <div class="box grid_12">
        <h3>
          <c:if test="${IS_SUPERUSER}">
            <span class="tag-editor">
              <c:set var="descriptor" value="${list.descriptor}" />
              <tiles:insert name="inlineTagEditor.tile">
                <tiles:put name="taggable" beanName="descriptor" />
                <tiles:put name="show" value="true" />
              </tiles:insert>
            </span>
          </c:if>
          ${list.size} ${list.prefix}
        </h3>
        <c:choose>
          <c:when test="${list.showLinksToObjects}">
            <c:forEach items="${list.items}" var="item" varStatus="status">
              <a href="<c:out value="${WEB_PROPERTIES['path']}" />objectDetails.do?id=${item.id}" target="new"
              title="Show '${item.value}' detail">${item.value}</a><c:if test="${status.count < list.size}">, </c:if>
            </c:forEach>
          </c:when>
          <c:otherwise>
            <c:forEach items="${list.items}" var="item" varStatus="status">
              ${item.value}<c:if test="${status.count < list.size}">, </c:if>
            </c:forEach>
          </c:otherwise>
        </c:choose>
      </div>
      <div style="clear:both;">&nbsp;</div>
    </c:forEach>
  </c:if>
</c:forEach>