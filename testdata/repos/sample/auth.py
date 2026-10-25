class AuthClient:
    def retry_authentication(self, request):
        if request.failed:
            return self.retry(request)
        return None

    def retry(self, request):
        return request
