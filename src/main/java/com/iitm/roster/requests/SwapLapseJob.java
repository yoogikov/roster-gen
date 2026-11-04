package com.iitm.roster.requests;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Lapses open swaps whose shift has started. The delay is {@code roster.lapse-delay-ms}. */
@Component
public class SwapLapseJob {

	private final SwapLifecycle swaps;

	public SwapLapseJob(SwapLifecycle swaps) {
		this.swaps = swaps;
	}

	@Scheduled(fixedDelayString = "${roster.lapse-delay-ms:60000}")
	public void lapse() {
		swaps.lapseOpen();
	}

}
