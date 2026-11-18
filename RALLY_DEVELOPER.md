# Rally Developer

Rally Developer is a cloud-based coding agent for this repository, powered by the official OpenAI Codex GitHub Action.

## What it does
- Runs on demand from GitHub Actions with a task supplied by the repository owner.
- Runs a scheduled technical audit every Monday.
- Inspects and edits the checked-out repository in an isolated GitHub-hosted runner.
- Runs Android Gradle tests and assembles a debug APK.
- Pushes successful changes to a dedicated agent branch and opens a pull request.
- Uploads the debug APK as a workflow artifact when one is produced.

It does not merge pull requests, publish releases, change repository settings, or access repository secrets other than the OpenAI API key provided to the Codex action.

## One-time setup

1. Create an OpenAI API key with API billing enabled. A ChatGPT subscription does not itself provide API credits.
2. In GitHub, open **FooP12/RallyCopilot-Android → Settings → Secrets and variables → Actions**.
3. Create a repository secret named `OPENAI_API_KEY` and paste the API key.
4. Ensure GitHub Actions are enabled for the repository.
5. Open **Actions → Rally Developer → Run workflow**. Enter a development task or leave it empty for a technical audit.

## Safety model
- Codex runs on a fresh GitHub-hosted Ubuntu runner.
- The workflow restricts the Codex sandbox to workspace edits and drops sudo privileges.
- Only the repository owner account `FooP12` is allowed to trigger the agent.
- Every change is committed to an agent branch and proposed through a pull request.
- The default branch is never modified directly by the agent.
- Review each pull request before merging.

## Limits
This is an event-driven cloud agent, not a permanently running server. Scheduled audits run weekly; on-demand development runs start from GitHub Actions. API usage and GitHub Actions minutes may incur charges or usage limits. The first run requires the repository secret above.
