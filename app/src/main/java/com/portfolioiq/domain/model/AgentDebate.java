package com.portfolioiq.domain.model;

/**
 * The case for and against one stock: the first argument of TradingAgents'
 * Bull researcher and of its Bear researcher, as the AI server reports them:
 * plain text, which the server cuts to 500 characters (the app does not check).
 * They are LLM-written debate speeches, each told to advocate one side; the
 * bear's rebuts the bull's. Either may be empty if that side said nothing.
 */
public class AgentDebate {

    private final String bullCase;
    private final String bearCase;

    public AgentDebate(String bullCase, String bearCase) {
        this.bullCase = bullCase == null ? "" : bullCase;
        this.bearCase = bearCase == null ? "" : bearCase;
    }

    /** The case for the stock (the bull's); may be empty. */
    public String getBullCase() { return bullCase; }

    /** The case against the stock (the bear's); may be empty. */
    public String getBearCase() { return bearCase; }
}
