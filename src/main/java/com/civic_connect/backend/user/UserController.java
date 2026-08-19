package com.civic_connect.backend.user;


import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse>getCurrentUser(Authentication authentication){

        String email= authentication.getName();
        User user=userRepository.findByEmail(email)
                .orElseThrow(()->new RuntimeException("user not found"));

        UserResponse response = new UserResponse(

                user.getUsername(),
                user.getId(),
                user.getProvider(),
                user.getEmail()

        );

        return ResponseEntity.ok(response);
    }
}
