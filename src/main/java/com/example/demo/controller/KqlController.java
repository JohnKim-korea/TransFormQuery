package com.example.demo.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.example.demo.service.KqlService;


@Controller
public class KqlController {

	@Autowired
	KqlService kqlService;

    @RequestMapping(value = "/", method = RequestMethod.GET)
    public String KqlParse() {
        return "parse/KqlParse";
    }

    @RequestMapping(value = "/study/transformQuery", method = RequestMethod.POST, produces = "application/json; charset=UTF-8")
    @ResponseBody
    public Map<String, Object> transformQuery(@RequestParam("query") String query) {
        Map<String, Object> map = new HashMap<>();
        try {
            String result = kqlService.divideTokenProcess(query);
            map.put("ok", true);
            map.put("result", result);
        } catch (Exception e) {
            map.put("ok", false);
            map.put("result", e.getMessage());
            System.out.println("오류메세지: " + e.getMessage());
        }
        return map;
    }
    
    @RequestMapping(value="/study/test", method = RequestMethod.POST,  produces="text/plain; charset=UTF-8")
    @ResponseBody
    public String test (@RequestParam("query") String query) {
    	String result = query + "_테스트합니다.";
    	
    	return result;
    }
}
