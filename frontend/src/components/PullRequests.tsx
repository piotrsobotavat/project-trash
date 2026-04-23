import { useEffect, useState } from "react";
import {
  fetchPullRequests,
  fetchDiff,
  fetchFullDiff,
  requestAiReview,
  type PullRequest,
  type DiffEntry,
  type FileDiff,
  type AiReviewResult,
} from "../api";

interface Props {
  repositoryId?: string;
}

export default function PullRequests({ repositoryId }: Props) {
  const [prs, setPrs] = useState<PullRequest[]>([]);
  const [status, setStatus] = useState("active");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const [selectedPr, setSelectedPr] = useState<number | null>(null);
  const [diffEntries, setDiffEntries] = useState<DiffEntry[]>([]);
  const [fullDiff, setFullDiff] = useState<FileDiff[] | null>(null);
  const [diffLoading, setDiffLoading] = useState(false);
  const [reviewResult, setReviewResult] = useState<AiReviewResult | null>(null);
  const [reviewLoading, setReviewLoading] = useState(false);

  useEffect(() => {
    setLoading(true);
    setError("");
    fetchPullRequests(repositoryId, status)
      .then(setPrs)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, [repositoryId, status]);

  const showDiff = async (prId: number) => {
    if (selectedPr === prId) {
      setSelectedPr(null);
      return;
    }
    setSelectedPr(prId);
    setDiffLoading(true);
    setFullDiff(null);
    setReviewResult(null);
    try {
      const entries = await fetchDiff(prId);
      setDiffEntries(entries);
    } catch (e: unknown) {
      setDiffEntries([]);
      setError(e instanceof Error ? e.message : "Failed to load diff");
    } finally {
      setDiffLoading(false);
    }
  };

  const showFullDiff = async (prId: number) => {
    setDiffLoading(true);
    try {
      const diff = await fetchFullDiff(prId);
      setFullDiff(diff);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "Failed to load full diff");
    } finally {
      setDiffLoading(false);
    }
  };

  const runAiReview = async (prId: number) => {
    setReviewLoading(true);
    setReviewResult(null);
    try {
      const result = await requestAiReview(prId);
      setReviewResult(result);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "AI review failed");
    } finally {
      setReviewLoading(false);
    }
  };

  return (
    <section className="panel">
      <div className="panel-header">
        <h2>Pull Requests</h2>
        <div className="status-filter">
          {["active", "completed", "abandoned"].map((s) => (
            <button
              key={s}
              className={status === s ? "active" : ""}
              onClick={() => setStatus(s)}
            >
              {s}
            </button>
          ))}
        </div>
      </div>

      {loading && <p className="loading">Loading pull requests...</p>}
      {error && <p className="error">{error}</p>}

      {!loading && prs.length === 0 && (
        <p className="empty">No {status} pull requests found.</p>
      )}

      <div className="pr-list">
        {prs.map((pr) => (
          <div key={pr.pullRequestId} className="pr-card">
            <div
              className="pr-header"
              onClick={() => showDiff(pr.pullRequestId)}
            >
              <span className={`pr-status pr-status--${pr.status}`}>
                {pr.status}
              </span>
              <strong className="pr-title">
                #{pr.pullRequestId} {pr.title}
              </strong>
              <span className="pr-meta">
                {pr.createdBy.displayName} &middot;{" "}
                {new Date(pr.creationDate).toLocaleDateString()} &middot;{" "}
                {pr.repository.name}
              </span>
            </div>

            <div className="pr-branches">
              {pr.sourceRefName.replace("refs/heads/", "")} →{" "}
              {pr.targetRefName.replace("refs/heads/", "")}
            </div>

            {selectedPr === pr.pullRequestId && (
              <div className="pr-diff">
                {diffLoading && <p className="loading">Loading diff...</p>}

                {!diffLoading && diffEntries.length > 0 && (
                  <>
                    <h4>Changed Files ({diffEntries.length})</h4>
                    <ul className="diff-files">
                      {diffEntries.map((d, i) => (
                        <li key={i}>
                          <span
                            className={`change-type change-type--${d.changeType?.toLowerCase()}`}
                          >
                            {d.changeType}
                          </span>
                          {d.item?.path ?? d.originalPath}
                        </li>
                      ))}
                    </ul>
                    <button
                      className="btn-full-diff"
                      onClick={() => showFullDiff(pr.pullRequestId)}
                    >
                      Show Full Diff
                    </button>
                    <button
                      className="btn-ai-review"
                      onClick={() => runAiReview(pr.pullRequestId)}
                      disabled={reviewLoading}
                    >
                      {reviewLoading ? "⟳ AI Reviewing..." : "◈ AI Review"}
                    </button>
                  </>
                )}

                {fullDiff && fullDiff.length > 0 && (
                  <div className="full-diff-container">
                    {fullDiff.map((file, i) => (
                      <div key={i} className="file-diff">
                        <div className="file-diff-header">
                          <span
                            className={`change-type change-type--${file.changeType?.toLowerCase()}`}
                          >
                            {file.changeType}
                          </span>
                          <span className="file-diff-path">{file.path}</span>
                          <span className="file-diff-stats">
                            <span className="stat-added">
                              +{file.linesAdded}
                            </span>
                            <span className="stat-removed">
                              -{file.linesRemoved}
                            </span>
                          </span>
                        </div>
                        {file.diff && (
                          <pre className="full-diff">
                            {file.diff.split("\n").map((line, j) => (
                              <span
                                key={j}
                                className={
                                  line.startsWith("---") ||
                                  line.startsWith("+++")
                                    ? "diff-line diff-line--header"
                                    : line.startsWith("@@")
                                      ? "diff-line diff-line--hunk"
                                      : line.startsWith("-")
                                        ? "diff-line diff-line--removed"
                                        : line.startsWith("+")
                                          ? "diff-line diff-line--added"
                                          : "diff-line"
                                }
                              >
                                {line}
                                {"\n"}
                              </span>
                            ))}
                          </pre>
                        )}
                      </div>
                    ))}
                  </div>
                )}

                {reviewLoading && (
                  <p className="loading">AI is reviewing this PR...</p>
                )}

                {reviewResult && (
                  <div className="ai-review-result">
                    <div className="ai-review-header">
                      <span className="ai-review-badge">◈ AI Review</span>
                      <span className="ai-review-meta">
                        Comment added to PR #{reviewResult.pullRequestId}
                      </span>
                    </div>
                    <div className="ai-review-body">{reviewResult.review}</div>
                  </div>
                )}
              </div>
            )}
          </div>
        ))}
      </div>
    </section>
  );
}
