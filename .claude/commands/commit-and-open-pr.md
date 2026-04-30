# commit-and-open-pr

## Objective
Safely review changes, validate quality, and create a pull request only if everything passes.

---

## Steps

### 1. Review Changes
- Analyze all modified files in the current branch.
- Check for:
    - Code quality issues (readability, duplication, bad naming)
    - Potential bugs or edge cases
    - Missing error handling
    - Security concerns
    - Violations of project conventions

### 2. Report Issues (If Any)
- If any issues are found:
    - Clearly list them with file names and reasoning.
    - DO NOT modify code.
    - Ask the user how to proceed.

### 3. Confirm Clean State
- If no issues are found:
    - Summarize the changes in 2–5 bullet points.
    - Ask for confirmation before proceeding to tests.

### 4. Run Tests
- Execute the project’s test suite.

### 5. Handle Test Failures
- If any tests fail:
    - Show failing tests and error output.
    - DO NOT modify code.
    - Ask the user how to proceed.

### 6. Prepare Commit
- Generate a commit message using this format:
    - Type: feat | fix | refactor | chore | test | docs
    - Structure:
      ```
      <type>: <short summary>
      ```
- Show the commit message to the user and ask for approval.

### 7. Commit & Push
- After approval:
    - Stage relevant changes
    - Commit using the approved message
    - Push to the remote branch

### 8. Create Pull Request
- Open a PR with:
    - Clear, concise title (same as commit summary)
    - Description including:
        - What changed
        - Why it was needed
        - Any risks or notes for reviewers
    - Use GitHub MCP server for creating the pull request

### 9. Final Output
- Provide:
    - PR link
    - Summary of actions taken

---

## General Rules
- NEVER modify code without explicit user approval.
- NEVER proceed past a step if it fails.
- ALWAYS show outputs (review, tests, commit message) before taking irreversible actions.

## Security Rules
- NEVER read or access sensitive configuration files:
  - application-local.yml
  - application-local.properties
  - .env files
  - secrets or key files

- If access is required:
  - STOP and ask the user explicitly