// set up basic settings
//
var base      = '10.1.2.30',
    host      = 'http://' + base,
    hostport  = 6150,
    localport = 9999;

// define server proxy
//
var express          = require('express'),
    app              = express(),
    https            = require('https'),
    http             = require('http'),
    valenceDevToken  = '*CNXDEV',
    valenceDev       = true,// true - running outside the portal, auto set sid with dev token
    queryString      = require('querystring'),
    url              = require('url'),
    httpProxy        = require('express-http-proxy'),
    valenceDataProxy = httpProxy(host + ':' + hostport, {
        forwardPath : function(req, res) {
            var parsedUrl = url.parse(req.url),
                path      = url.parse(req.url).path;

            //add the valence Dev Token if needed when it's GET request
            //
            if (req.method === 'GET' && valenceDev){
                var query     = queryString.parse(parsedUrl.query),
                    pathName  = parsedUrl.pathname,
                    updatedQueryString;

                query.sid = valenceDevToken;
                updatedQueryString = queryString.stringify(query);

                if (updatedQueryString != null && updatedQueryString !== ''){
                    path = pathName + '?' + updatedQueryString;
                }
            }
            return path;
        },
        decorateRequest : function(req) {
            if (req.method !== 'GET' && valenceDev){
                //add the Valence Dev Token if needed if it's not a GET request
                //
                var body = req.bodyContent.toString('utf8');

                body = queryString.parse(body);
                body.sid = valenceDevToken;
                req.bodyContent = queryString.stringify(body);
            }
            return req;
        }
    }),
    valenceProxy      = httpProxy(host + ':' + hostport, {
        forwardPath : function(req, res) {
            return url.parse(req.url).path;
        }
    });

app.all('/valence/*', valenceDataProxy);
app.all('/portal/*', valenceProxy);
app.all('/php/*',valenceProxy);
app.all('/extjs/*',valenceProxy);
app.all('/desktop/autocodeApps/*',valenceProxy);
//app.all('/resources/*',valenceProxy);
app.all('/resources/*',express.static(__dirname));
app.all('/packages/local/*',valenceProxy);

// serve static folders from this local machine
//
app.use('/mpbio_atomicvx/', express.static(__dirname));
app.use('/', express.static(__dirname));

// begin listening on local port
//
app.listen(localport);