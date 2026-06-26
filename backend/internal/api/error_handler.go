package api

import (
	"errors"
	"net/http"
)

// ProblemErrorHandler is an api.StdHTTPServerOptions.ErrorHandlerFunc that renders
// oapi-codegen path-parameter binding failures as RFC 7807 Problem Details
// (application/problem+json). It satisfies REQ-NF-001: no API error path returns
// Content-Type text/plain.
//
// Security note (T-2-12): only the static OpenAPI parameter name (e.g. "id") is
// surfaced in the detail string. err.Error() is never passed to WriteProblem,
// preventing information disclosure of Go internals or stack traces.
func ProblemErrorHandler(w http.ResponseWriter, r *http.Request, err error) {
	var ipfErr *InvalidParamFormatError
	if errors.As(err, &ipfErr) {
		WriteProblem(w, http.StatusBadRequest, "about:blank", "Bad Request",
			"path parameter '"+ipfErr.ParamName+"' is malformed")
		return
	}
	WriteProblem(w, http.StatusBadRequest, "about:blank", "Bad Request",
		"invalid request parameter")
}
