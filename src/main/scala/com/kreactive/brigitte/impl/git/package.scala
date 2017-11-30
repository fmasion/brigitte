package com.kreactive.brigitte.impl

import com.kreactive.brigitte.State
import org.eclipse.jgit.api.{Git, TransportCommand}
import org.eclipse.jgit.transport.CredentialsProvider

package object git {
  type GitState[+T] = State[Git, T]
  implicit class TransportCommandOps[C <: TransportCommand[C, _]](f: C) {
    def maybeSetCredentials(creds: Option[CredentialsProvider]) = creds.fold(f)(f.setCredentialsProvider)
  }
}
