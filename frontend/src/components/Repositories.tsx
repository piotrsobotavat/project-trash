import { useEffect, useState } from "react";
import { fetchRepositories, type Repository } from "../api";

export default function Repositories() {
  const [repos, setRepos] = useState<Repository[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    fetchRepositories()
      .then(setRepos)
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <p className="loading">Loading repositories...</p>;
  if (error) return <p className="error">{error}</p>;

  return (
    <section className="panel">
      <h2>Repositories ({repos.length})</h2>
      <table>
        <thead>
          <tr>
            <th>Name</th>
            <th>Project</th>
            <th>Default Branch</th>
          </tr>
        </thead>
        <tbody>
          {repos.map((r) => (
            <tr key={r.id}>
              <td>{r.name}</td>
              <td>{r.project?.name ?? "—"}</td>
              <td>{r.defaultBranch?.replace("refs/heads/", "") ?? "—"}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
