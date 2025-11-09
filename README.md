//Create file normal on MyEclipse and can be empty and next in terminal on myeclipse:
git add .gitignore
git commit -m "Add .gitignore for Java/MyEclipse project"
git push
// when push and want next change I did add autor, but if only want change I think have to only 'git commit --amend'
git commit --amend --reset-author
//Be careful: rewriting history is fine if you haven’t pushed yet, but if you already pushed, you’ll need to force-push (git push --force)
