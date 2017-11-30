package com.kreactive.brigitte.impl.git

import com.jcraft.jsch.{JSch, Session}
import configs.Configs
import org.eclipse.jgit.transport.OpenSshConfig.Host
import org.eclipse.jgit.transport.{CredentialsProvider, JschConfigSessionFactory, SshSessionFactory, UsernamePasswordCredentialsProvider}
import org.eclipse.jgit.util.FS

/**
  * Representation of a remote git repo
 *
  * @param uri the uri of the repo (ssh or https)
  * @param credsO the credentials needed for the repo
  */
sealed abstract class GitRemote(val uri: String, val credsO: Option[CredentialsProvider]) {
  def init() = ()
}

/**
  * Helper model to define credentials in config
  * @param user
  * @param password
  */
case class ConfigUserPassCredentialsProvider(user: String, password: String) extends UsernamePasswordCredentialsProvider(user, password)

/**
  * Uses https and user/password authentication
  * @param uri the uri for the repo
  * @param credentials the credentials for the repo
  */
case class GitRemoteHttps(override val uri: String, credentials: CredentialsProvider) extends GitRemote(uri, Some(credentials))

case class SshHost(name: String, known_hosts: String)

case class OverriddenSshSessionFactory(
                                       privateKey: String,
                                       host: SshHost) extends JschConfigSessionFactory {
  override def configure(hc: Host, session: Session) = ()

  override def createDefaultJSch(fs: FS): JSch =
    jschMadeRight(super.createDefaultJSch(fs))

  override def getJSch(hc: Host, fs: FS): JSch =
    jschMadeRight(super.getJSch(hc, fs))

  private def jschMadeRight(jsch:JSch) = {
    jsch.removeAllIdentity()
    jsch.addIdentity(privateKey)
    jsch.setKnownHosts(host.known_hosts)
    jsch
  }
}

/**
  * Uses ssh with keygen authentication. Relies by default on server keys. This can be overridden by giving the optional fields
  *
  * @param uri the uri for the repo
  * @param credentials possible credentials to connect to remote host with ssh
  */
case class GitRemoteSsh(override val uri: String, credentials: Option[OverriddenSshSessionFactory]) extends GitRemote(uri, None) {
  override def init() = credentials.foreach(SshSessionFactory.setInstance)
}
object GitRemote {
  implicit val credentialsConfig: Configs[CredentialsProvider] = Configs[ConfigUserPassCredentialsProvider].map(x => x)
  implicit val configs: Configs[GitRemote] = {
    Configs.get[String]("uri").flatMap{
      case web if web.startsWith("https") => Configs.derive[GitRemoteHttps].map(x => x)
      case _ => Configs.derive[GitRemoteSsh].map(x => x)
    }
  }
}