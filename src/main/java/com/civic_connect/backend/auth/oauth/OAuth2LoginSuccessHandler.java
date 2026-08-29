package com.civic_connect.backend.auth.oauth;

import com.civic_connect.backend.auth.security.JwtService;

import com.civic_connect.backend.common.enums.Role;
import com.civic_connect.backend.user.entity.User;
import com.civic_connect.backend.user.Repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;
@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler{

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final String frontendCallbackUrl;
    public OAuth2LoginSuccessHandler(UserRepository userRepository,
                                    JwtService jwtService,
                                    @Value("${app.frontend-callback-url:http://localhost:5500}") String frontendCallbackUrl) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.frontendCallbackUrl = frontendCallbackUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        OAuth2User oauthuser=(OAuth2User)authentication.getPrincipal();

        String email=oauthuser.getAttribute("email");
        String username =oauthuser.getAttribute("name");
        Optional<User> ExistingUser=userRepository.findByEmail(email);


        if(ExistingUser.isEmpty()){
            User user =new User();
            user.setEmail(email);
            user.setUsername(username);
            user.setProvider("GOOGLE");
            user.setRole(Role.CITIZEN);

            userRepository.save(user);
        }
        String token = jwtService.generateToken(email);

        response.sendRedirect(frontendCallbackUrl + "?token=" + token);
    }

}
