package com.kreactive.brigitte.impl
package git

import better.files._
import com.kreactive.brigitte.State
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand.ResetType
import org.eclipse.jgit.lib._
import org.eclipse.jgit.transport.FetchResult

object GitState {

  def create(branch: String, r: GitRemote, folder: File): Git = {
    Dsl.rm(folder)
    Git.cloneRepository().
      setBranch(branch).
      setDirectory(folder.toJava).
      setURI(r.uri).
      maybeSetCredentials(r.credsO).
      call()
  }

  /**
    * helper to write custom git state commands
    */
  def apply[T](f: Git => T): GitState[T] = State((g: Git) => (g, f(g)))

  /**
    * git checkout <branch>
    */
  def checkout(branch: String): GitState[Ref] = apply{
    _.checkout().setName(branch).call()
  }

  /**
    * git fetch
    */
  def fetchOrigin(implicit remote: GitRemote): GitState[FetchResult] = apply{
    _.fetch().maybeSetCredentials(remote.credsO).call()
  }

  /**
    * git reset --hard HEAD
    */
  def resetHard: GitState[Ref] = apply{
    _.reset().setRef(Constants.HEAD).setMode(ResetType.HARD).call()
  }

  /**
    * - if there is a remote:
    *         git branch -d <branch>
    *         git fetch
    *         git checkout branch
    * - if there's no remote:
    *         git reset --hard HEAD
    *         git checkout <branch>
    */
  def cleanBranch(branch: String, remote: Option[GitRemote]): GitState[Ref] =
    remote.fold(resetHard)(implicit r => forcePull(branch)) >> checkout(branch)

  /**
    * git stash
    * @return false if nothing was there to be stashed
    */
  def stash: GitState[Boolean] = apply {
    _.stashCreate().setIncludeUntracked(true).call() ne null
  }

  /**
    * git stash pop
    */
  private def unstash: GitState[ObjectId] = apply {
    _.stashApply().call()
  }

  /**
    * if wasStashed, git stash pop
    * else do nothing
    */
  def safeUnstash(wasStashed: Boolean): GitState[Unit] =
    if (wasStashed) unstash map (_ => ()) else unit(())

  /**
    * repo object
    */
  def repo: GitState[Repository] = apply {
    _.getRepository
  }

  /**
    * list heads of all remote branches. This is done without fetching everything to avoid unnecessary overhead
    */
  def remoteHeads: GitState[Map[String, Ref]] = apply {
    import scala.collection.JavaConverters._
    val remoteRef = "refs/heads/(.*)".r
    _.lsRemote().setHeads(true).callAsMap().asScala.toMap.map{
      case (remoteRef(remoteBranch), ref) => remoteBranch -> ref
    }
  }

  /**
    * The reference to the head of the given branch on remote
    * @param branch the name of the local branch to which we want to compare the remote tracked one
    * @param differentRemoteName `true` if remote and local branches might have different names
    * @return the reference to the last commit on remote branch
    */
  def remoteHead(branch: String, differentRemoteName: Boolean = false): GitState[Ref] = repo >>= { r =>
    remoteHeads map { heads =>
      val remoteBranch =
        if (differentRemoteName)
          r.shortenRemoteBranchName(BranchTrackingStatus.of(r, branch).getRemoteTrackingBranch)
        else branch
      heads(remoteBranch)
    }
  }

  /**
    * ObjectId for last commit of given branch (or Ref)
    */
  def lastOf(branch: String): GitState[ObjectId] = repo map {
    _.resolve(branch)
  }

  /**
    * ObjectId for HEAD commit
    */
  def head: GitState[ObjectId] = lastOf(Constants.HEAD)

  /**
    * git fetch
    * git branch -d <branch>
    * git checkout branch (from origin)
    * @param branch *must* be the name, not the ref
    * @param remote
    * @return
    */
  def forcePull(branch: String)(implicit remote: GitRemote): GitState[Ref] =
    fetchOrigin >> forceCreate(branch) >> checkout(branch)

  private def forceCreate(branch: String): GitState[Ref] = apply {
    _.branchCreate().setForce(true).setName(branch).setStartPoint(s"origin/$branch").call()
  }

  /**
    * The name of the current branch we're on
    */
  def branchName: GitState[String] = apply {
    _.getRepository.getBranch
  }

  /**
    * Simpler unit, to avoid typing
    */
  def unit[T](t: T): GitState[T] = State.unit[Git, T](t)
}