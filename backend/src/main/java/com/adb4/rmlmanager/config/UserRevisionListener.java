package com.adb4.rmlmanager.config;

import com.adb4.rmlmanager.entity.UserRevision;
import com.adb4.rmlmanager.security.AppUserPrincipal;
import org.hibernate.envers.RevisionListener;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class UserRevisionListener implements RevisionListener {
    @Override
    public void newRevision(Object revisionEntity) {
        UserRevision revision = (UserRevision) revisionEntity;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken)
                && auth.getPrincipal() instanceof AppUserPrincipal principal) {
            revision.setUserId(principal.getId());
        }
    }
}