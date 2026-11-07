package com.iitm.roster.web;

import com.iitm.roster.domain.Role;
import com.iitm.roster.repo.StaffRecord;
import com.iitm.roster.repo.StaffStore;
import com.iitm.roster.repo.UserAccount;
import com.iitm.roster.security.AccountService;
import com.iitm.roster.security.CurrentActor;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Admin-only doctor directory: add, rename, activate, and create or reset logins. */
@Controller
@SuppressFBWarnings(value = "EI_EXPOSE_REP2",
		justification = "the stores and services are Spring singletons injected on purpose; sharing them is the point")
public class DoctorsController {

	private final StaffStore staff;
	private final AccountService accounts;
	private final CurrentActor current;

	public DoctorsController(StaffStore staff, AccountService accounts, CurrentActor current) {
		this.staff = staff;
		this.accounts = accounts;
		this.current = current;
	}

	@GetMapping("/doctors")
	String list(Model model, HttpServletResponse response) {
		if (!admin()) {
			return forbidden(model, response);
		}
		return doctorsPage(model, null);
	}

	@PostMapping("/doctors")
	String add(@RequestParam String id, @RequestParam String name, Model model, HttpServletResponse response) {
		if (!admin()) {
			return forbidden(model, response);
		}
		String notice;
		try {
			StaffRecord saved = accounts.addDoctor(id, name);
			notice = "added " + saved.id() + " (" + saved.name() + ")";
		} catch (RuntimeException ex) {
			notice = ex.getMessage();
		}
		return doctorsPage(model, notice);
	}

	@PostMapping("/doctors/{id}/rename")
	String rename(@PathVariable String id, @RequestParam String name, Model model, HttpServletResponse response) {
		if (!admin()) {
			return forbidden(model, response);
		}
		String notice;
		try {
			StaffRecord saved = accounts.rename(id, name);
			notice = "renamed " + saved.id() + " to " + saved.name();
		} catch (RuntimeException ex) {
			notice = ex.getMessage();
		}
		return doctorsPage(model, notice);
	}

	@PostMapping("/doctors/{id}/active")
	String setActive(@PathVariable String id, @RequestParam boolean active, Model model, HttpServletResponse response) {
		if (!admin()) {
			return forbidden(model, response);
		}
		String notice;
		try {
			accounts.setActive(id, active);
			notice = id + (active ? " is active" : " is inactive");
		} catch (RuntimeException ex) {
			notice = ex.getMessage();
		}
		return doctorsPage(model, notice);
	}

	@PostMapping("/doctors/{id}/login")
	String createLogin(@PathVariable String id, @RequestParam String username, Model model,
			HttpServletResponse response) {
		if (!admin()) {
			return forbidden(model, response);
		}
		String notice;
		try {
			String temporary = accounts.createStaffLogin(username, id);
			notice = "login " + username + " for " + id + "; temporary password: " + temporary
					+ " (shown once)";
		} catch (RuntimeException ex) {
			notice = ex.getMessage();
		}
		return doctorsPage(model, notice);
	}

	@PostMapping("/doctors/logins/{username}/reset")
	String reset(@PathVariable String username, Model model, HttpServletResponse response) {
		if (!admin()) {
			return forbidden(model, response);
		}
		String notice;
		try {
			String temporary = accounts.resetPassword(username);
			notice = "reset " + username + "; temporary password: " + temporary + " (shown once)";
		} catch (RuntimeException ex) {
			notice = ex.getMessage();
		}
		return doctorsPage(model, notice);
	}

	@PostMapping("/doctors/logins/{username}/enabled")
	String setEnabled(@PathVariable String username, @RequestParam boolean enabled, Model model,
			HttpServletResponse response) {
		if (!admin()) {
			return forbidden(model, response);
		}
		String notice;
		try {
			accounts.setEnabled(username, enabled);
			notice = username + (enabled ? " enabled" : " disabled");
		} catch (RuntimeException ex) {
			notice = ex.getMessage();
		}
		return doctorsPage(model, notice);
	}

	private String doctorsPage(Model model, String notice) {
		List<StaffRecord> doctors = staff.findAllRecords();
		Map<String, UserAccount> logins = new LinkedHashMap<>();
		for (StaffRecord doctor : doctors) {
			accounts.findLoginForStaff(doctor.id()).ifPresent(login -> logins.put(doctor.id(), login));
		}
		model.addAttribute("doctors", doctors);
		model.addAttribute("logins", logins);
		model.addAttribute("notice", notice);
		return "doctors";
	}

	private boolean admin() {
		return current.get().role() == Role.ADMIN;
	}

	private static String forbidden(Model model, HttpServletResponse response) {
		response.setStatus(HttpServletResponse.SC_FORBIDDEN);
		model.addAttribute("notice", "only an admin can manage doctors");
		return "forbidden";
	}

}
