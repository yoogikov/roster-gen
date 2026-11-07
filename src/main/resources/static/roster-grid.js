(function () {
	var form = document.getElementById("edit-form");
	if (!form) {
		return;
	}
	var typeSelect = form.elements.type;
	function syncMoveFields() {
		var move = typeSelect.value === "move";
		document.getElementById("staff-label").hidden = move;
		document.getElementById("from-label").hidden = !move;
		document.getElementById("to-label").hidden = !move;
	}
	typeSelect.addEventListener("change", syncMoveFields);
	syncMoveFields();

	document.querySelectorAll("[data-edit='true']").forEach(function (cell) {
		cell.addEventListener("click", function () {
			form.elements.date.value = cell.dataset.date;
			form.elements.shift.value = cell.dataset.shift;
		});
	});

	document.querySelectorAll("[data-weekly='true']").forEach(function (cell) {
		cell.addEventListener("click", function () {
			var weeklyForm = document.getElementById("weekly-off-form");
			if (!weeklyForm) {
				return;
			}
			weeklyForm.elements.fromDate.value = cell.dataset.date;
		});
	});
}());
