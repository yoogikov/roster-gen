package com.iitm.roster.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Form login page so a shared PC can sign out. */
@Controller
public class LoginController {

	@GetMapping("/login")
	String login(@RequestParam(value = "error", required = false) String error,
			@RequestParam(value = "logout", required = false) String logout, Model model) {
		if (error != null) {
			model.addAttribute("notice", "wrong username or password");
		}
		if (logout != null) {
			model.addAttribute("notice", "signed out");
		}
		return "login";
	}

}
