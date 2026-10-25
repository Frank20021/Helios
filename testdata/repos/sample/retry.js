function retryAuthentication(request) {
  if (request.failed) {
    retry(request);
  }
}
