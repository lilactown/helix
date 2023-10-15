.PHONY: clean run
.DEFAULT_GOAL := run

clean:
	rm -rf public/dev/js

.make_npm: package.json
	npm install --no-fund --no-audit --no-progress --loglevel=error
	touch $@

run: clean .make_npm
	npm run shadow-cljs watch dev
