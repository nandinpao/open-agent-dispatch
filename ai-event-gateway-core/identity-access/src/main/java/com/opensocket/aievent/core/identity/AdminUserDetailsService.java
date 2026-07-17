package com.opensocket.aievent.core.identity;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AdminUserDetailsService implements UserDetailsService {
    private final AdminIdentityRepository repository;

    public AdminUserDetailsService(AdminIdentityRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return repository.findByUsername(username)
                .map(AdminPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("Admin user not found"));
    }
}
