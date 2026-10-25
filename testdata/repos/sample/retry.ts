export function retryAuthentication(request: AuthRequest): void {
  if (request.failed) {
    retry(request);
  }
}

export const retry = (request: AuthRequest): void => {
  return;
};
