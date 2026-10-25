package auth

// Request is an authentication attempt.
type Request struct {
	Failed bool
}

// retryAuthentication retries a failed login.
func retryAuthentication(req Request) error {
	if req.Failed {
		return retry(req)
	}
	return nil
}

func retry(req Request) error {
	return nil
}
