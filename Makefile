.PHONY: deploy build clean

deploy:
	mvn clean package -Pdeploy

build:
	mvn clean package

clean:
	mvn clean
