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

<#if downToThreshold??>
	<p>
		<div class="data_title"><b>Test Data in short supply</b></div>
	</p>
	<#list downToThreshold as element>
		<br>
		<div><i><span class="title">Environment: ${element.environment.environmentName}</span></i></div>
		<div><i><span class="title">System: ${element.environment.systemName}</span></i></div>
		<br>
		<table class="table_data">
			<tr>
				<td class="cell header_red" style='width:280pt; background:#FF5B5B'>
					<b><i><div class="title">Data Type</div></i></b>
				</td>
				<td class="cell header_red" style='width:100pt; background:#FF5B5B'>
					<b><i><div class="title" style='text-align:center'>Available</div></i></b>
				</td>
				<td class="cell header_red" style='width:100pt; background:#FF5B5B'>
					<b><i><div class="title" style='text-align:center'>Occupied</div></i></b>
				</td>
				<td class="cell header_red" style='width:100pt; background:#FF5B5B'>
					<b><i><div class="title" style='text-align:center'>Occupied Today</div></i></b>
				</td>
			</tr>
			<#list element.data as item>
				<tr>
					<td class="cell" style='width:280pt'><div class="title">${item.context}</div></td>
					<td class="cell" style='width:100pt'><div class="title" style='text-align:center'>${item.available}</div></td>
					<td class="cell header_grey" style='width:100pt'><div class="title" style='text-align:center'>${item.occupied}</div></td>
					<td class="cell header_grey" style='width:100pt'>
						<div class="title" style='text-align:center'>${item.occupiedToday}</div>
					</td>
				</tr>
			</#list>
		</table>
	</#list>
	<br><br>
</#if>

<#if upToThreshold??>
	<p><b><div class="data_title">Sufficient Test Data</div></b></p>
	<#list upToThreshold as element>
		<br>
		<div><i><span class="title">Environment: ${element.environment.environmentName}</span></i></div>
		<div><i><span class="title">System: ${element.environment.systemName}</span></i></div>
		<table class="table_data">
			<tr>
				<td class="cell header_green" style='width:280pt; background:#A9DA74'>
					<b><i><div class="title">Data Type</div></i></b>
				</td>
				<td class="cell header_green" style='width:100pt; background:#A9DA74'>
					<b><i><div class="title" style='text-align:center'>Available</div></i></b>
				</td>
				<td class="cell header_green" style='width:100pt; background:#A9DA74'>
					<b><i><div class="title" style='text-align:center'>Occupied</div></i></b>
				</td>
				<td class="cell header_red" style='width:100pt; background:#FF5B5B'>
					<b><i><div class="title" style='text-align:center'>Occupied Today</div></i></b>
				</td>
			</tr>
			<#list element.data as item>
				<tr>
					<td class="cell" style='width:280pt'><div class="title">${item.context}</div></td>
					<td class="cell" style='width:100pt'><div class="title" style='text-align:center'>${item.available}</div></td>
					<td class="cell header_grey" style='width:100pt'><div class="title" style='text-align:center'>${item.occupied}</div></td>
					<td class="cell header_grey" style='width:100pt'>
						<div class="title" style='text-align:center'>${item.occupiedToday}</div>
					</td>
				</tr>
			</#list>
		</table>
	</#list>
</#if>

</body>
</html>
