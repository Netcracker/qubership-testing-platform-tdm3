<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
	<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
	<meta name="viewport" content="width=device-width, initial-scale=1.0"/>
	<style type="text/css">
		.data_title {
			font-size:14pt;
			color:#203864
		}
		.title {
			color:#323E4F
		}
		.table_data {
			border-collapse:collapse
		}
		.cell {
			border:solid windowtext 1.2pt;
			padding:0in 5.4pt 0in 5.4pt;
			align:top
		}
	</style>
</head>
<body>

<p>
<div class="data_title"><b>Test Data Bulk Cleanup Results</b></div>
</p>

<br>
<div><i><span class="title">Project: ${projectName}</span></i></div>
<div><i><span class="title">Environment: ${environmentName}</span></i></div>
<div><i><span class="title">System: ${systemName}</span></i></div>
<br>
<table class="table_data">
	<tr>
		<td class="cell header_red" style='width:280pt; background:#28aafa'>
			<b><i><div class="title">Table Title</div></i></b>
		</td>
		<td class="cell header_red" style='width:100pt; background:#28aafa'>
			<b><i><div class="title">Total Records</div></i></b>
		</td>
		<td class="cell header_red" style='width:100pt; background:#28aafa'>
			<b><i><div class="title" style='text-align:center'>Removed Records</div></i></b>
		</td>
	</tr>
	<#list items as item>
		<tr>
			<td class="cell" style='width:280pt'><div class="title">${item.tableTitle}</div></td>
			<#if item.results??>
				<td class="cell" style='width:100pt'><div class="title" style='text-align:center'>${item.results.recordsTotal}</div></td>
				<td class="cell" style='width:100pt'><div class="title" style='text-align:center'>${item.results.recordsRemoved}</div></td>
			<#else>
				<td colspan="2" class="cell" style='width:100pt'><div class="title" style='text-align:center'>${item.exception}</div></td>
			</#if>
		</tr>
	</#list>
</table>
<br><br>

</body>
</html>
