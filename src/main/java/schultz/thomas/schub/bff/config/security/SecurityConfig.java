package schultz.thomas.schub.bff.config.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import schultz.thomas.schub.bff.config.ContactProperties;

import java.util.Arrays;
import java.util.List;

/**
 * La chaîne de sécurité du BFF.
 *
 * <p>{@code anyRequest().authenticated()} reste le filet, mais il n'est plus le seul contrôle :
 * chaque route porte désormais sa permission en {@code @PreAuthorize} — c'est ce que
 * {@code @EnableMethodSecurity} rend possible, et c'est le préalable absolu à l'ouverture de la
 * connexion Discord (plan §A.0). Trois chemins restent publics et le sont explicitement :
 * {@code /auth/**}, la sonde de santé et le statut public des serveurs.</p>
 *
 * <p>Le compte par mot de passe et son {@code InMemoryUserDetailsManager} sont conservés : leur
 * retrait est le dernier lot du chantier (A.6), dans une PR à part, après qu'une connexion
 * Discord a fonctionné en prod.</p>
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({JwtProperties.class, AuthAdminProperties.class, DiscordOAuthProperties.class,
        ContactProperties.class})
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AuthAdminProperties authAdminProperties;

    @Value("${auth.cors.allowed-origins:http://localhost:18090,http://localhost:5173}")
    private String allowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          AuthAdminProperties authAdminProperties) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authAdminProperties = authAdminProperties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF désactivé, et c'est ce que `SameSite=Lax` sur le cookie de session rend
                // tenable : toutes les écritures sont POST/PUT/DELETE, et `Lax` ne joint pas le
                // cookie à une écriture initiée par un autre site. Le jour où une écriture passe
                // en GET, cette ligne devient une faille — c'est le seul invariant à tenir
                // (plan §A.3).
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/game-servers/public-status").permitAll()
                        // Le formulaire de contact du portfolio : PUBLIC, et en écriture.
                        //
                        // C'est la seule route du BFF dans ce cas, et elle n'a pas d'alternative —
                        // un formulaire de contact derrière une authentification ne sert à rien.
                        // Sans cette ligne elle répondrait 401 à tout le monde, `anyRequest()`
                        // étant `authenticated()`.
                        //
                        // Ce qui la protège est ailleurs, dans ContactService : limitation de
                        // débit par IP, champ leurre, et Turnstile si un secret est fourni.
                        // AUCUNE de ces trois couches ne se retire sans en ajouter une autre.
                        .requestMatchers(HttpMethod.POST, "/contact").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        // Sans cette ligne, le navigateur reçoit le jeton renouvelé et le cache au front : un
        // en-tête de réponse non exposé est illisible en JavaScript, et la réémission glissante
        // serait invisible. Disparaîtra au lot A.3, quand le jeton passera en cookie httpOnly.
        configuration.setExposedHeaders(List.of(JwtAuthenticationFilter.RENEWED_TOKEN_HEADER));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        var admin = User.withUsername(authAdminProperties.username())
                .password(passwordEncoder.encode(authAdminProperties.password()))
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(admin);
    }

    @Bean
    public AuthenticationProvider authenticationProvider(UserDetailsService userDetailsService,
                                                         PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
