package com.iitm.roster.scheduler;

/** Fills the open slots of a roster. Returns {@code Infeasible} rather than throwing when it cannot. */
public interface RosterGenerator {

	GenerationResult generate(GenerationRequest request);

}
