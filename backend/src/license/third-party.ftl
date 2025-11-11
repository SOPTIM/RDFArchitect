# Third-Party Licenses

<#if dependencyMap?size == 0>
    No dependencies.
<#else>
<#list licenseMap as entry>
<#assign projects = entry.getValue()/>
<#list projects as project>
### ${project.name}
- **Package:** ${project.groupId}
- **Version:** ${project.version}
- **License:** ${entry.getKey()}
<#if project.url??>
- **URL:** [${project.url}](${project.url})
</#if>

</#list>
</#list>
</#if>
