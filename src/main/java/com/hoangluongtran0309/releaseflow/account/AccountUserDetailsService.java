package com.hoangluongtran0309.releaseflow.account;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    public AccountUserDetailsService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        // A removed member cannot sign in: their row has no password, and no lookup finds it.
        return appUserRepository.findByEmailAndRemovedAtIsNull(EmailAddress.canonicalize(email))
                .map(ReleaseFlowPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("Account not found."));
    }
}
