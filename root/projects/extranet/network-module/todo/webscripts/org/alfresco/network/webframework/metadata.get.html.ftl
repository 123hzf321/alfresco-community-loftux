{
	<@serialize object=object includeChildren=includeChildren includeContent=includeContent/>
}

<#macro serialize object includeChildren includeContent>
	"isContainer" : ${object.isContainer?string}
	,
	"isDocument" : ${object.isDocument?string}
	,
	"url" : "${object.url}"
	,
	"downloadUrl" : "${object.downloadUrl}"
	,
	"mimetype" : "${mimetype}"
	,
	"size" : "${object.size}"
	,
	"displayPath" : "${object.displayPath}"
	,
	"qnamePath" : "${object.qnamePath}"
	,
	"icon16" : "${object.icon16}"
	,
	"icon32" : "${object.icon32}"
	,
	"isLocked" : ${object.isLocked?string}
	,
	"id" : "${object.id}"
	,
	"nodeRef" : "${object.nodeRef}"
	,
	"name" : "${object.name}"
	,
	"type" : "${object.type}"
	,
	"isCategory" : ${object.isCategory?string}
	,
	
<#if object.children?exists>
	<#if object.children?size &gt; 0>
		"hasChildren" : true
	<#else>
		"hasChildren" : false
	</#if>
</#if>
	
<#if object.properties?exists>
	,
	"properties" :
	{
	<#assign first = true>
	<#list object.properties?keys as key>
		<#if object.properties[key]?exists>
			<#assign val = object.properties[key]>

			<#assign renderable = false>
			<#if val?is_string == true>
				<#assign renderable = true>
			<#elseif val?is_date == true>
				<#assign renderable = true>
			<#elseif val?is_boolean == true>
				<#assign renderable = true>
			</#if>
			<#if renderable == true>			
				<#if first == false>
					,
				</#if>
				<#if val?is_string == true>			
					"${key}" : "${val?js_string}"
				<#elseif val?is_date == true>			
					"${key}" : "${val?datetime}"
				<#elseif val?is_boolean == true>			
					"${key}" : ${val?string}
				</#if>
				<#assign first = false>				
			</#if>
		</#if>
	</#list>
	}
</#if>

<#if includeChildren && object.children?exists>
	,
	"children" :
	[
		<#assign first = true>
		<#list object.children as child>
			<#if first == false>
		,
			</#if>
		{
			<@serialize object=child includeChildren=false includeContent=includeContent/>
		}
			<#assign first = false>	
		</#list>
	]
<#else>
	,
	"children" : [ ]
</#if>

</#macro>