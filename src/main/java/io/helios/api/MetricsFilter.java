package io.helios.api;

import io.helios.jobs.JobService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class MetricsFilter extends OncePerRequestFilter {
    private final JobService svc;

    public MetricsFilter(JobService svc) {
        this.svc = svc;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        filterChain.doFilter(request, response);
        String path = request.getRequestURI();
        if (!path.startsWith("/ui/") && !"/".equals(path)) {
            svc.metrics().observeHttp(response.getStatus());
        }
    }
}
