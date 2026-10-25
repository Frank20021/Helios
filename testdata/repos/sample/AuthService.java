public class AuthService {
    public void retryAuthentication(Request request) {
        if (request.failed) {
            retry(request);
        }
    }

    private void retry(Request request) {
        return;
    }
}
