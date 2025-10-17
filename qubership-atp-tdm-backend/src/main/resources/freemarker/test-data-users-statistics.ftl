<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
    <style type="text/css">
        .data_title {
            font-size:14pt;
            color:#FF5B5B
        }
        .title {
            color:#323E4F
        }
        .table_data {
            border-collapse:collapse;
        }
        .cell {
            border:1px solid black;
        }
    </style>
</head>

<body>
<#if elements??>
    <#list elements as element>
        <#if (element.dates?size > 13)>
            <p><div class="data_title">
            <b>You have selected a period of ${element.dates?size} days.</b>
            <b>The information in the report is shown for the first 14 days.</b>
            <br>
            <b>If you need a report for the entire selected period, select the report format CSV.</b>
            </div></p>
        </#if>
        <#break>
    </#list>

    <#list elements as element>
        <br>
        <div><i><span class="title">User: ${element.user}</span></i></div>
        <br>
        <table class="table_data">
            <tr>
                <td class="cell" style='width:200px; background:#FF5B5B'>
                    <b><i><div class="title">TYPE</div></i></b>
                </td>
                <td class="cell" style='width:200px; background:#FF5B5B'>
                    <b><i><div class="title" style='text-align:center'>ENVIRONMENT</div></i></b>
                </td>
                <td class="cell" style='width:200px; background:#FF5B5B'>
                    <b><i><div class="title" style='text-align:center'>SYSTEM</div></i></b>
                </td>

                <#list element.dates as date>
                    <#if (date?index < 14)>
                        <td class="cell" style='width:70px; background:#FF5B5B'>
                            <b><i><div class="title" style='text-align:center'>${date}</div></i></b>
                        </td>
                    <#else> <#break>
                    </#if>
                </#list>

            </tr>
            <#list element.items as item>
                <tr>
                    <td class="cell" style='width:200px'><div class="title">${item.context}</div></td>
                    <td class="cell" style='width:200px'><div class="title" style='text-align:center'>${item
                            .environment}</div></td>
                    <td class="cell" style='width:200px'><div class="title" style='text-align:center'>${item
                            .system}</div></td>
                    <#list item.counts as count>
                        <#if (count?index < 14)>
                            <td class="cell header_grey" style='width:70px'>
                                <div class="title" style='text-align:center'>${count}</div>
                            </td>
                        <#else> <#break>
                        </#if>
                    </#list>
                </tr>
            </#list>
        </table>
    </#list>
    <br><br>
    <#else>
        <p><div class="data_title"><b>No data for the selected period!</b></div></p>
</#if>

</body>
</html>
