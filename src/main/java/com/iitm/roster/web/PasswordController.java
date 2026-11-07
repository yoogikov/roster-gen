package com.iitm.roster.web;

import com.iitm.roster.security.AccountService;
import com.iitm.roster.security.CurrentActor;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Any signed-in user can change their own password. */
@Controller
@SuppressFBWarnings(value = "EI_EXPOSE_REP2",
		justification = "AccountService is a Spring singleton injected on purpose; sharing it is the point")
public class PasswordController {

	private final AccountService accounts;
	private final CurrentActor current;

	public PasswordController(AccountService accounts, CurrentActor current) {
		this.accounts = accounts;
		this.current = current;
	}

	@GetMapping("/password")
	String form(Model model) {
		model.addAttribute("username", current.get().username());
		return "password";
	}

	@PostMapping("/password")
	String change(@RequestParam String oldPassword, @RequestParam String newPassword, Model model) {
		String username = current.get().username();
		model.addAttribute("username", username);
		try {
			accounts.changeOwnPassword(username, oldPassword, newPassword);
			model.addAttribute("notice", "password changed; use the new one next time you sign in");
		} catch (RuntimeException ex) {
			model.addAttribute("notice", ex.getMessage());
		}
		return "password";
	}

}
