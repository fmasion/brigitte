package com.kreactive.brigitte.impl.git

case class GitConfig(branch: String, remote: Option[GitRemote] = None)
