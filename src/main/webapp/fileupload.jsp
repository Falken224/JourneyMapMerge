<!DOCTYPE html>
<!--
To change this license header, choose License Headers in Project Properties.
To change this template file, choose Tools | Templates
and open the template in the editor.
-->
<!DOCTYPE html>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@page import="java.io.File" %>
<html lang="en">
    <head>
        <title>Upload your zip file</title>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    </head>
    <body>
        <h2>Servers</h2>
        <ul>
            <% request.setAttribute("serverDir",new File(System.getProperty("jmmerge.data.dir","/var/data/jmmerge"))); %>
            <c:forEach items="${serverDir.listFiles()}" var="theFile">
                <c:if test="${theFile.directory}">
                  <li><a href="/jmmerge/map/<c:out value="${theFile.name}"/>"><c:out value="${theFile.name}"/></a></li>
                </c:if>
            </c:forEach>
        </ul>
        <form method="POST" action="/jmmerge/maps" enctype="multipart/form-data" >
            File:
            <input type="file" name="file" id="file" /> <br/>
            Server:
            <input type="text" name="server" id="server" /> <br/>
            </br>
            <input type="submit" value="Upload" name="upload" id="upload" />
        </form>
    </body>
</html>
