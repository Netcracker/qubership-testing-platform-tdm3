<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
    <meta name=Generator content="Microsoft Word 15 (filtered medium)">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <style type="text/css">
        .data-table {
            color: #000000;
            font-size: 13px;
            line-height: 18px;
            font-style: normal;
            border: 1px solid #C4C4C4;
            box-sizing: border-box;
            border-collapse: collapse;
        }
        .data-table_row{
            border: 1px solid #C4C4C4;
        }
        .data-table_header {
            width:155.8pt;
            border:solid #C4C4C4 1.0pt;
            background:#DEE0E7;
            padding:0in 5.4pt 0in 5.4pt
        }
        .data-table_cell {
            border: 1px solid #C4C4C4;
            padding: 2px 15px 2px 10px;
        }
        .title {
            color:#353C4E;
            font-size: 17px;
        }
        .warning {
            background: #FFD77F;
            text-align: center;
        }
        .failure {
            background: #ff0000;
            text-align: center;
        }
        .success {
            background: #7ef8b3;
            text-align: center;
        }
    </style>
</head>

<body lang=EN-US link=blue vlink=purple>
<#if statistic??>

    <div><p class="title">AVAILABLE DATA REPORT</p></div>
    <table class="data-table">
        <tr class="data-table_row">
            <th class="data-table_header">TABLE</th>
            <th class="data-table_header">ADDITIONAL OPTIONS</th>
            <th style="width:50pt;" class="data-table_header">QUANTITY</th>
        </tr>
        <tr class="data-table_row">
            <td colspan=3 style='border-top:none;color:#353C4E;border-left:solid #C4C4C4 1.0pt;border-bottom:solid #DEE0E7 1.0pt;border-right:solid #C4C4C4 1.0pt;padding:0in 5.4pt 0in 5.4pt'><b><i><span style="color:#353C4E">ENVIRONMENT:</span></i></b> ${environment}    CASE: ${statistic.description}</td>
        </tr>
        <#list statistic.statistics as table>
            <tr class="data-table_row">
                <td rowspan="${table.options?size}" style='border:solid #C4C4C4 1.0pt;border-top:none;padding:0in 5.4pt 0in 5.4pt'>
                    <span style='line-height:113%;font-family:"Calibri",sans-serif;color:black'>${table.tableTitle}</span>
                </td>
                <#list table.options?keys as key>
                <td style='border-top:none;border-left:none;border-bottom:solid #C4C4C4 1.0pt;border-right:solid #C4C4C4 1.0pt;padding:0in 5.4pt 0in 5.4pt'>
                    <p class=MsoNormal style='line-height:113%'>
                        <span style='line-height:113%;font-family:"Calibri",sans-serif;color:black'>${key}</span>
                    </p>
                </td>
                <#if table.options[key] == 0 >
                    <td class="failure" style='border-top:none;border-left:none;border-bottom:solid #C4C4C4 1.0pt;border-right:solid #C4C4C4 1.0pt;padding:0in 5.4pt 0in 5.4pt'>
                        <p class=MsoNormal align=center style='text-align:center;line-height:113%'>
				            <span style='line-height:113%;font-family:"Calibri",sans-serif;color:black'>${table.options[key]}</span>
                        </p>
                    </td>
                <#elseif table.options[key] <= threshold >
                    <td class="warning" style='border-top:none;border-left:none;border-bottom:solid #C4C4C4 1.0pt;border-right:solid #C4C4C4 1.0pt;padding:0in 5.4pt 0in 5.4pt'>
                        <p class=MsoNormal align=center style='text-align:center;line-height:113%'>
                            <span style='line-height:113%;font-family:"Calibri",sans-serif;color:black'>${table.options[key]}</span>
                        </p>
                    </td>
                <#else>
                    <td class="success" style='border-top:none;border-left:none;border-bottom:solid #C4C4C4 1.0pt;border-right:solid #C4C4C4 1.0pt;padding:0in 5.4pt 0in 5.4pt'>
                        <p class=MsoNormal align=center style='text-align:center;line-height:113%'>
                            <span style='line-height:113%;font-family:"Calibri",sans-serif;color:black'>${table.options[key]}</span>
                        </p>
                    </td>
                </#if>
            </tr>
            </#list>
        </#list>
    </table>
    <#list highcharts as highcharts>
        ${highcharts}
    </#list>
    <#else>
    <p><div class="data_title"><b>No data for the selected period!</b></div></p>
</#if>
</body>
</html>
