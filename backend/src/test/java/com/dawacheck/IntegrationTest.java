package com.dawacheck;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base for the tests that need the whole application.
 *
 * Every subclass shares one Spring context, because they all declare the same
 * configuration: starting the application once and reusing it keeps the suite
 * fast enough to run on every change, which is the only way it gets run.
 *
 * The context comes up seeded, so these tests assert against the knowledge base
 * the application ships with. Tests that write must put back what they changed.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTest {
}
