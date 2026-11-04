package com.dawacheck.service;

import com.dawacheck.domain.AppUser;
import com.dawacheck.repo.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Resolves the signed-in account.
 *
 * Every read and write in the application goes through here and then through a
 * repository method scoped by owner id, which is how "each account sees only its
 * own family" (section 14) is enforced rather than merely intended.
 */
@Service
public class CurrentUser {

  private final UserRepository users;

  public CurrentUser(UserRepository users) {
    this.users = users;
  }

  public Optional<AppUser> find() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null
        || "anonymousUser".equals(auth.getPrincipal())) {
      return Optional.empty();
    }
    return users.findByEmailIgnoreCase(auth.getName());
  }

  public AppUser require() {
    return find().orElseThrow(() -> new NotFoundException("Please log in again."));
  }

  public Long requireId() {
    return require().id;
  }
}
