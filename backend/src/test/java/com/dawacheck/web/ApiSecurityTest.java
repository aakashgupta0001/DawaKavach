package com.dawacheck.web;

import com.dawacheck.IntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Who can see and do what.
 *
 * The thing being protected here is a family's medicine list. The documentation
 * states that an account sees only its own family, and these tests are what
 * make that a property of the application rather than an intention.
 */
@ExtendWith(SpringExtension.class)
class ApiSecurityTest extends IntegrationTest {

  @Autowired
  private WebApplicationContext context;

  @Autowired
  private ObjectMapper json;

  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  private String body(Object value) throws Exception {
    return json.writeValueAsString(value);
  }

  /** Logs in and returns the session that carries the authentication. */
  private HttpSession logIn(String email, String password) throws Exception {
    MvcResult result = mvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("email", email, "password", password))))
        .andExpect(status().isOk())
        .andReturn();
    return result.getRequest().getSession();
  }

  /* ---------------- not logged in ---------------- */

  @Test
  void anAnonymousVisitorCannotReadAnyFamily() throws Exception {
    mvc.perform(get("/api/members")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/members/fm1/cabinet")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/members/fm1/alerts")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/members/fm1/summary.pdf")).andExpect(status().isUnauthorized());
  }

  @Test
  void anAnonymousVisitorCannotSearchTheCatalogueEither() throws Exception {
    // The catalogue is not secret, but leaving it open would make the API a
    // free medicine-data endpoint for anyone who found the host.
    mvc.perform(get("/api/medicines/search").param("q", "dolo"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void askingWhoIsLoggedInIsAnOrdinaryQuestionWithAnOrdinaryAnswer() throws Exception {
    // Every page asks this on load to decide whether to show itself, so "nobody"
    // has to be a success rather than a 401.
    mvc.perform(get("/api/auth/me")).andExpect(status().isNoContent());
  }

  /* ---------------- logged in ---------------- */

  @Test
  void theDemoAccountSeesItsOwnFamily() throws Exception {
    HttpSession session = logIn("aakash@example.com", "dawacheck");
    mvc.perform(get("/api/members").session((org.springframework.mock.web.MockHttpSession) session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(4)))
        .andExpect(jsonPath("$[0].nickname").value("Dadaji"));
  }

  @Test
  void aWrongPasswordIsRefusedWithoutSayingWhichPartWasWrong() throws Exception {
    // Telling an attacker that the email exists is half the work done for them.
    mvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("email", "aakash@example.com", "password", "wrongpassword"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Email or password is not correct."));

    mvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("email", "nobody@example.com", "password", "wrongpassword"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Email or password is not correct."));
  }

  @Test
  void aPasswordIsNeverEchoedBack() throws Exception {
    MvcResult result = mvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("email", "aakash@example.com", "password", "dawacheck"))))
        .andExpect(status().isOk())
        .andReturn();
    assertThat(result.getResponse().getContentAsString())
        .doesNotContain("dawacheck")
        .doesNotContain("password");
  }

  /* ---------------- one family cannot see another ---------------- */

  @Test
  @Transactional
  void anotherAccountCannotReachThisFamilyAtAll() throws Exception {
    // Registering creates an account with its own empty family. Rolled back
    // after the test so the demo data is left as it was.
    MvcResult registered = mvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of(
                "name", "Someone Else",
                "email", "someone-else@example.com",
                "password", "longenoughpassword",
                "consent", true))))
        .andExpect(status().isOk())
        .andReturn();
    var session = (org.springframework.mock.web.MockHttpSession)
        registered.getRequest().getSession();

    // Their own family is empty.
    mvc.perform(get("/api/members").session(session))
        .andExpect(status().isOk())
        .andExpect(content().json("[]"));

    // And the demo family is not theirs to read. A 404 rather than a 403,
    // because "that member exists but is not yours" is itself worth not saying.
    mvc.perform(get("/api/members/fm1").session(session))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/members/fm1/cabinet").session(session))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/members/fm1/alerts").session(session))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/members/fm1/reminders").session(session))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/members/fm1/summary").session(session))
        .andExpect(status().isNotFound());
  }

  @Test
  @Transactional
  void anOrdinaryAccountCannotTouchTheKnowledgeBase() throws Exception {
    // The knowledge base is what every family's safety check is built on, so
    // changing it is not something an ordinary account may do.
    MvcResult registered = mvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of(
                "name", "Ordinary Owner",
                "email", "ordinary@example.com",
                "password", "longenoughpassword",
                "consent", true))))
        .andExpect(status().isOk())
        .andReturn();
    var session = (org.springframework.mock.web.MockHttpSession)
        registered.getRequest().getSession();

    mvc.perform(get("/api/admin/overview").session(session)).andExpect(status().isForbidden());
    mvc.perform(post("/api/admin/reset-demo").session(session))
        .andExpect(status().isForbidden());
  }

  /* ---------------- registration ---------------- */

  @Test
  @Transactional
  void registrationRecordsConsentAndRefusesWithoutIt() throws Exception {
    // DPDP Act 2023: the account is not created without the family agreeing to
    // what is stored, so there is no record to have to delete afterwards.
    mvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of(
                "name", "No Consent",
                "email", "no-consent@example.com",
                "password", "longenoughpassword",
                "consent", false))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(
            org.hamcrest.Matchers.containsString("agree")));
  }

  @Test
  @Transactional
  void registrationRefusesAShortPasswordAndADuplicateEmail() throws Exception {
    mvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of(
                "name", "Short", "email", "short@example.com",
                "password", "abc", "consent", true))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(
            org.hamcrest.Matchers.containsString("8 characters")));

    mvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of(
                "name", "Taken", "email", "aakash@example.com",
                "password", "longenoughpassword", "consent", true))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(
            org.hamcrest.Matchers.containsString("already exists")));
  }

  /* ---------------- bad input ---------------- */

  @Test
  void anIdOfTheWrongKindIsRefusedRatherThanReadingTheWrongTable() throws Exception {
    HttpSession session = logIn("aakash@example.com", "dawacheck");
    mvc.perform(get("/api/members/m1/cabinet")
            .session((org.springframework.mock.web.MockHttpSession) session))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(
            org.hamcrest.Matchers.containsString("family member id")));
  }

  @Test
  void aMissingResourceIsAFourZeroFourAndNotAServerFault() throws Exception {
    // A browser asks for /favicon.ico whether or not one exists; logging a
    // stack trace for each one buries the real problems.
    mvc.perform(get("/favicon.ico")).andExpect(status().isNotFound());
  }

  @Test
  void anErrorBodyCarriesAMessageTheFrontendCanShow() throws Exception {
    HttpSession session = logIn("aakash@example.com", "dawacheck");
    mvc.perform(get("/api/members/fm99999")
            .session((org.springframework.mock.web.MockHttpSession) session))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Family member not found."))
        .andExpect(jsonPath("$.status").value(404));
  }
}
