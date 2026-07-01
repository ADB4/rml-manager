package com.adb4.rmlmanager.config;

import com.adb4.rmlmanager.entity.UserRevision;
import org.hibernate.envers.RevisionListener;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public class UserRevisionListener implements RevisionListener {
    @Override
    public void newRevision(Object revisionEntity) {
        UserRevision revision = (UserRevision) revisionEntity;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
            && !(auth instanceof AnonymousAuthenticationToken)) {
            revision.setUserId(UUID.fromString(auth.getName()));
        }
    }
}
