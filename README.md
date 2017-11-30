BriGitte
=================================

Une lib qui permet d'obtenir une source de donnée versionnée avec jGit

# Utilisation

Ajoute dans tes dépendences l'artefact suivant :

    "com.kreactive" %% "brigitte" % "0.1.1"
    
Puis instancie le `Fetcher` qui te convient.

## Qu'est-ce qu'un `Fetcher` ?

C'est un trait qui fournit une source d'un type donné.

## Quels `Fetcher`s sont implémentés actuellement ?

Tous les `Fetcher`s actuellement implémentés fonctionnent sur le même système : une `Source` de tics qui déclenchent 
la récupération de la référence à une machine à états (typiquement, le système de fichiers de l'OS), 
et un traducteur qui extrait de cette référence des données immutables.

### `FileSystemFetcher`

Les tics sont générés régulièrement, la machine à états est le système de fichiers, et le traducteur est donné en paramètre.

### `GitFSFetcher`

Les tics correspondent à l'arrivée d'un nouveau commit sur la branche désignée d'un repo git. 
La branche est vérifiée régulièrement pour s'assurer de l'arrivée d'un commit. Le traducteur est donné en paramètre.

### `HookedGitFSFetcher`

Idem que pour le `GitFSFetcher`, sauf que l'application écoute sur un webhook pour savoir si un nouveau commit est arrivé 
sur un repo GitLab. On peut quand même faire une vérification régulière si on ne fait pas confiance à GitLab.    


cross publish sur bintray :
    + publish          // cross publish sur les version scala
    bintrayRelease     // crée la release de la version


### Crédits :

Cyrille Corpet      https://github.com/zozoens31

Julien Blondeau     https://github.com/captainju

Rémi Lavolée        https://github.com/rlavolee

