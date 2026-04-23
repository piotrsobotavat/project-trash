const BASE = "/azure";

export interface Repository {
  id: string;
  name: string;
  url: string;
  defaultBranch?: string;
  size?: number;
  project?: { name: string };
}

export interface PullRequest {
  pullRequestId: number;
  title: string;
  status: string;
  createdBy: { displayName: string; uniqueName?: string };
  creationDate: string;
  repository: { name: string; id: string };
  sourceRefName: string;
  targetRefName: string;
  description?: string;
}

export interface DiffEntry {
  changeType?: string;
  item?: { path: string };
  originalPath?: string;
}

export interface FileDiff {
  path: string;
  changeType: string;
  linesAdded: number;
  linesRemoved: number;
  diff: string;
}

export async function fetchRepositories(): Promise<Repository[]> {
  const res = await fetch(`${BASE}/repositories`);
  if (!res.ok) throw new Error(`Failed to fetch repositories: ${res.status}`);
  return res.json();
}

export async function fetchPullRequests(
  repositoryId?: string,
  status = "active",
  top = 25,
): Promise<PullRequest[]> {
  const params = new URLSearchParams({ status, top: String(top) });
  if (repositoryId) params.set("repositoryId", repositoryId);
  const res = await fetch(`${BASE}/pull-requests?${params}`);
  if (!res.ok) throw new Error(`Failed to fetch pull requests: ${res.status}`);
  return res.json();
}

export async function fetchDiff(prId: number): Promise<DiffEntry[]> {
  const res = await fetch(`${BASE}/pull-requests/${prId}/diff`);
  if (!res.ok) throw new Error(`Failed to fetch diff: ${res.status}`);
  const data = await res.json();
  return data.changes ?? [];
}

export async function fetchFullDiff(prId: number): Promise<FileDiff[]> {
  const res = await fetch(`${BASE}/pull-requests/${prId}/full-diff`);
  if (!res.ok) throw new Error(`Failed to fetch full diff: ${res.status}`);
  const data = await res.json();
  return data.files ?? [];
}

export interface AiReviewResult {
  pullRequestId: number;
  review: string;
  commentThreadId: string | number;
}

export async function requestAiReview(prId: number): Promise<AiReviewResult> {
  const res = await fetch(`/ai/review/pull-requests/${prId}`);
  if (!res.ok) throw new Error(`AI review failed: ${res.status}`);
  return res.json();
}
