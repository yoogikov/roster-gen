(function () {
	var form = document.getElementById("edit-form");
	if (!form) {
		return;
	}
	document.querySelectorAll("[data-edit='true']").forEach(function (cell) {
		cell.addEventListener("click", function () {
			var text = cell.textContent.trim();
			form.elements.date.value = cell.dataset.date;
			form.elements.shift.value = cell.dataset.shift;
			form.elements.post.value = "GENERAL";
			if (text.length === 0) {
				var id = window.prompt("Doctor id to assign on general duty");
				if (!id) {
					return;
				}
				form.elements.type.value = "assign";
				form.elements.staffId.value = id.trim();
			} else {
				var existing = window.prompt("Doctor id to take off this shift", "");
				if (!existing) {
					return;
				}
				form.elements.type.value = "unassign";
				form.elements.staffId.value = existing.trim();
			}
			form.submit();
		});
	});
}());
